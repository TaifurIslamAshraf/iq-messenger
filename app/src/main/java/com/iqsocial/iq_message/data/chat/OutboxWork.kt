package com.iqsocial.iq_message.data.chat

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.media.UploadQueue

/**
 * Emptying the queue when the app is not the one asking.
 *
 * The hole this fills was the biggest one left in working offline. Everything else
 * waited for the app: a message written in a tunnel went when something inside a
 * running app noticed the network was back, so closing the app meant the message sat
 * there until it was opened again. Somebody who writes a message on the underground
 * and puts their phone in their pocket expects it to have gone by the time they take
 * it out, and it had not.
 *
 * Handed to the system instead. The request is stored by [WorkManager] rather than
 * held in this process, so it survives the app being closed and the phone being
 * restarted, and Android runs it when its one condition is met - that there is a
 * network.
 */
object OutboxWork {

    private const val TAG = "OutboxWork"

    /**
     * One request at a time, under one name.
     *
     * [ExistingWorkPolicy.KEEP] rather than REPLACE: the work is "empty the queue",
     * not "send this message", so ten messages written in a row want the one job
     * that was already asked for - replacing it each time would push the start back
     * every time somebody typed.
     */
    private const val NAME = "outbox"

    fun scheduleFor(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }.onFailure {
            // Not fatal: a running app still drains on its own. Said out loud
            // because what is lost is exactly the case nobody is watching - the
            // queue going out while the app is closed.
            Log.w(TAG, "Could not hand the outbox to the system", it)
        }
    }

    /** Signing out: there is nothing left to send, and nobody to send it as. */
    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
        }.onFailure { Log.w(TAG, "Could not call off the outbox work", it) }
    }
}

/**
 * The job itself.
 *
 * Suspends until the queue is empty rather than starting it and returning, because a
 * worker that returns is a worker the system may stop - and stopping it halfway
 * would leave a message marked as sending that nothing is sending.
 */
class OutboxWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val token = SessionStore(app).token
        // Signed out between the work being asked for and the system running it.
        // Nothing to do and nothing wrong: whatever was queued belonged to an
        // account that is no longer here, and the sign-out emptied it.
        if (token.isNullOrBlank()) return Result.success()

        return runCatching {
            ChatMessages.recoverInterrupted(app)
            Outbox.drainNow(app)
            UploadQueue.resumeNow(app, token)
            Result.success()
        }.getOrElse {
            // Asking to be run again rather than reporting failure. Anything that
            // got this far is a message somebody wrote, and the queue has already
            // decided for itself what is worth giving up on.
            Log.w("OutboxWork", "The queue did not empty; asking for another go", it)
            Result.retry()
        }
    }
}
