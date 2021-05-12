package org.thoughtcrime.securesms.loki.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPoller
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPoller
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupV2Poller
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.database.DatabaseFactory
import java.util.concurrent.TimeUnit

class BackgroundPollWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val TAG = "BackgroundPollWorker"

        @JvmStatic
        fun schedulePeriodic(context: Context) {
            Log.v(TAG, "Scheduling periodic work.")
            val workRequest = PeriodicWorkRequestBuilder<BackgroundPollWorker>(5, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

            WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(
                            TAG,
                            ExistingPeriodicWorkPolicy.REPLACE,
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
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
            val privateChatsPromise = SnodeAPI.getMessages(userPublicKey).map { envelopes ->
                envelopes.map { envelope ->
                    // FIXME: Using a job here seems like a bad idea...
                    MessageReceiveJob(envelope.toByteArray(), false).executeAsync()
                }
            }
            promises.addAll(privateChatsPromise.get())

            // Closed groups
            promises.addAll(ClosedGroupPoller().pollOnce())

            // Open Groups
            val openGroups = DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats().map { (_,chat)->
                OpenGroup(chat.channel, chat.server, chat.displayName, chat.isDeletable)
            }
            for (openGroup in openGroups) {
                val poller = OpenGroupPoller(openGroup)
                promises.add(poller.pollForNewMessages())
            }

            val openGroupsV2 = DatabaseFactory.getLokiThreadDatabase(context).getAllV2OpenGroups().values.groupBy(OpenGroupV2::server)

            openGroupsV2.values.map { groups ->
                OpenGroupV2Poller(groups)
            }.forEach { poller ->
                promises.add(poller.compactPoll(true).map{ /*Unit*/ })
            }

            // Wait till all the promises get resolved
            all(promises).get()

            return Result.success()
        } catch (exception: Exception) {
            Log.e(TAG, "Background poll failed due to error: ${exception.message}.", exception)
            return Result.retry()
        }
    }

     class BootBroadcastReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.v(TAG, "Boot broadcast caught.")
                schedulePeriodic(context)
            }
        }
    }
}
