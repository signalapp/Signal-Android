package org.thoughtcrime.securesms.loki.api

import android.content.Context
import androidx.work.*
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities

/**
 * Delegates the [OpenGroupUtilities.updateGroupInfo] call to the work manager.
 */
class PublicChatInfoUpdateWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val TAG = "PublicChatInfoUpdateWorker"

        private const val DATA_KEY_SERVER_URL = "server_uRL"
        private const val DATA_KEY_CHANNEL = "channel"
        private const val DATA_KEY_ROOM = "room"

        @JvmStatic
        fun scheduleInstant(context: Context, serverUrl: String, room :String) {
            val workRequest = OneTimeWorkRequestBuilder<PublicChatInfoUpdateWorker>()
                    .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(workDataOf(
                            DATA_KEY_SERVER_URL to serverUrl,
                            DATA_KEY_ROOM to room
                    ))
                    .build()

            WorkManager
                    .getInstance(context)
                    .enqueue(workRequest)
        }

        @JvmStatic
        fun scheduleInstant(context: Context, serverURL: String, channel: Long) {
            val workRequest = OneTimeWorkRequestBuilder<PublicChatInfoUpdateWorker>()
                    .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(workDataOf(
                            DATA_KEY_SERVER_URL to serverURL,
                            DATA_KEY_CHANNEL to channel
                    ))
                    .build()

            WorkManager
                    .getInstance(context)
                    .enqueue(workRequest)
        }
    }

    override fun doWork(): Result {
        val serverUrl = inputData.getString(DATA_KEY_SERVER_URL)!!
        val channel = inputData.getLong(DATA_KEY_CHANNEL, -1)
        val room = inputData.getString(DATA_KEY_ROOM)

        val isOpenGroupV2 = !room.isNullOrEmpty() && channel == -1L

        if (!isOpenGroupV2) {
            val publicChatId = OpenGroup.getId(channel, serverUrl)

            return try {
                Log.v(TAG, "Updating open group info for $publicChatId.")
                OpenGroupUtilities.updateGroupInfo(context, serverUrl, channel)
                Log.v(TAG, "Open group info was successfully updated for $publicChatId.")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update open group info for $publicChatId", e)
                Result.failure()
            }
        } else {
            val openGroupId = "$serverUrl.$room"

            return try {
                Log.v(TAG, "Updating open group info for $openGroupId.")
                OpenGroupUtilities.updateGroupInfo(context, serverUrl, room!!)
                Log.v(TAG, "Open group info was successfully updated for $openGroupId.")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update open group info for $openGroupId", e)
                Result.failure()
            }

        }
    }
}
