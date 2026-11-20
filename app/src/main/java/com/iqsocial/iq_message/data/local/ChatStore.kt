package com.iqsocial.iq_message.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.iqsocial.iq_message.data.auth.SessionStore

/**
 * The messages this phone has, on this phone.
 *
 * One database for the signed-in account, opened once and kept for the life of the
 * process. Not encrypted: that is a separate decision with a separate cost, and
 * leaving it out is worth saying plainly rather than discovering later - the thread
 * is readable by anything that can read the app's own files, which on an unrooted
 * phone is the app and the backup system.
 */
@Database(
    entities = [MessageRow::class, ConversationRow::class, OutboxRow::class, UploadRow::class],
    version = 3,
    exportSchema = false
)
abstract class ChatStore : RoomDatabase() {

    abstract fun chats(): ChatDao

    companion object {

        @Volatile
        private var instance: ChatStore? = null

        private const val DB = "iq_chat.db"
        private const val OWNER = "iq_chat_owner"
        private const val TAG = "ChatStore"

        fun of(context: Context): ChatStore =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Records whose messages these are.
         *
         * Called when an account signs in. Without it the store would be marked for
         * whoever was signed in when it was first opened in this process, which after
         * a sign-out is nobody.
         */
        fun claim(context: Context, uid: String) {
            if (uid.isBlank()) return
            context.applicationContext
                .getSharedPreferences(OWNER, Context.MODE_PRIVATE)
                .edit()
                .putString("uid", uid)
                .apply()
        }

        /**
         * Throws the database away if it belongs to somebody else.
         *
         * The sign-out already empties it, and most of the time that is the end of
         * the matter. This is for when it is not: the emptying is started and not
         * waited for, so a process killed in the moment between signing out and the
         * wipe finishing leaves one account's messages on disk - and the next person
         * to sign in on this phone would have been shown them, because the chat list
         * draws what is stored before the server has answered.
         *
         * Checked here because this is the one place every read goes through, and
         * done by deleting the file rather than by emptying tables because it runs
         * before Room opens it - no transaction to half-finish, nothing to wait for.
         *
         * A blank stored owner means a database from before this existed, or one
         * opened while signed out; neither is evidence of another account, so neither
         * is grounds for throwing away somebody's queued messages.
         */
        private fun discardIfAnotherAccount(app: Context) {
            val prefs = app.getSharedPreferences(OWNER, Context.MODE_PRIVATE)
            val kept = prefs.getString("uid", "").orEmpty()
            val now = SessionStore(app).load()?.uid.orEmpty()

            if (kept.isNotBlank() && kept != now) {
                Log.w(TAG, "The stored chats belong to another account; starting clean")
                // Takes the -wal and -shm with it, which deleting the one file by
                // hand would not.
                app.deleteDatabase(DB)
                prefs.edit().remove("uid").apply()
            }
            if (now.isNotBlank()) prefs.edit().putString("uid", now).apply()
        }

        /**
         * Adds the upload table, keeping everything else.
         *
         * A written-out migration rather than letting Room start again, because by
         * now this database holds things that exist nowhere else: a message queued
         * while offline, a recording that was never uploaded. Wiping on upgrade
         * would lose them, and it would lose them silently - the app would simply
         * open one day with somebody's unsent message gone.
         */
        private val ADD_UPLOADS = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `uploads` (" +
                        "`clientId` TEXT NOT NULL, " +
                        "`conversationId` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`thumbPath` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`size` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`aspect` REAL NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`waveform` TEXT NOT NULL, " +
                        "`replyToId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`clientId`))"
                )
            }
        }

        /**
         * Remembers the full-size choice for an upload that has not been prepared.
         *
         * Needed because holding the bytes and preparing them came apart: the file
         * is copied the moment it is picked, and prepared afterwards, so the choice
         * has to survive in between. Defaulted to 1 - rows written before this
         * existed were all prepared already, and nothing will read it on them.
         */
        private val ADD_UPLOAD_HD = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `uploads` ADD COLUMN `hd` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * Migrations on the way up; starting again only on the way down.
         *
         * A downgrade means somebody has installed an older build over a newer one,
         * which is a developer on this phone rather than anybody's real use of the
         * app, and there is no sensible way to turn a newer schema back into an
         * older one.
         */
        private fun build(app: Context): ChatStore {
            // Before Room opens it, never after: once the file is open, throwing it
            // away is a transaction rather than a delete.
            discardIfAnotherAccount(app)
            return Room.databaseBuilder(app, ChatStore::class.java, DB)
                .addMigrations(ADD_UPLOADS, ADD_UPLOAD_HD)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
        }
    }
}
