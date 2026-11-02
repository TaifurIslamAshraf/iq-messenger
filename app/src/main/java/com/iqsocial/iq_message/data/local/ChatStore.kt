package com.iqsocial.iq_message.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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
    version = 2,
    exportSchema = false
)
abstract class ChatStore : RoomDatabase() {

    abstract fun chats(): ChatDao

    companion object {

        @Volatile
        private var instance: ChatStore? = null

        fun of(context: Context): ChatStore =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
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
         * Migrations on the way up; starting again only on the way down.
         *
         * A downgrade means somebody has installed an older build over a newer one,
         * which is a developer on this phone rather than anybody's real use of the
         * app, and there is no sensible way to turn a newer schema back into an
         * older one.
         */
        private fun build(app: Context): ChatStore =
            Room.databaseBuilder(app, ChatStore::class.java, "iq_chat.db")
                .addMigrations(ADD_UPLOADS)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
