package org.thoughtcrime.securesms.loki.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.loki.api.SnodeAPI
import java.util.concurrent.TimeUnit

class BackgroundPollWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val TAG = "BackgroundPollWorker"

        private const val RETRY_ATTEMPTS = 3

        @JvmStatic
        fun scheduleInstant(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<BackgroundPollWorker>()
                    .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

            WorkManager
                    .getInstance(context)
                    .enqueue(workRequest)
        }

        @JvmStatic
        fun schedulePeriodic(context: Context) {
            Log.v(TAG, "Scheduling periodic work.")
            val workRequest = PeriodicWorkRequestBuilder<BackgroundPollWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

            WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(
                            TAG,
                            ExistingPeriodicWorkPolicy.KEEP,
                            workRequest
                    )
        }
    }

    override fun doWork(): Result {
        if (TextSecurePreferences.getLocalNumber(context) == null) {
            Log.v(TAG, "Background poll is canceled due to the Session user is not set up yet.")
            return Result.failure()
        }

        try {
            Log.v(TAG, "Performing background poll.")
            val promises = mutableListOf<Promise<Unit, Exception>>()

            // Private chats
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)
            val privateChatsPromise = SnodeAPI.shared.getMessages(userPublicKey).map { envelopes ->
                envelopes.forEach {
                    PushContentReceiveJob(context).processEnvelope(SignalServiceEnvelope(it), false)
                }
            }
            promises.add(privateChatsPromise)

            // Closed groups
            val sskDatabase = DatabaseFactory.getSSKDatabase(context)
            ClosedGroupPoller.configureIfNeeded(context, sskDatabase)
            promises.addAll(ClosedGroupPoller.shared.pollOnce())

            // Open Groups
            val openGroups = DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats().map { it.value }
            for (openGroup in openGroups) {
                val poller = PublicChatPoller(context, openGroup)
                promises.add(poller.pollForNewMessages())
            }

            // Wait till all the promises get resolved
            all(promises).get()

            return Result.success()
        } catch (exception: Exception) {
            Log.v(TAG, "Background poll failed due to error: ${exception.message}.", exception)

            return if (runAttemptCount < RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

     class BootBroadcastReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.v(TAG, "Boot broadcast caught.")
                BackgroundPollWorker.scheduleInstant(context)
                BackgroundPollWorker.schedulePeriodic(context)
            }
        }
    }
}
