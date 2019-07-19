package org.thoughtcrime.securesms.loki

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.whispersystems.signalservice.loki.api.LokiAPI
import org.whispersystems.signalservice.loki.api.LokiAPIDatabaseProtocol

class BackgroundPollWorker(private val userHexEncodedPublicKey: String, private val apiDatabase: LokiAPIDatabaseProtocol, context: Context, parameters: WorkerParameters) : Worker(context, parameters) {

    override fun doWork(): Result {
        return try {
            LokiAPI(userHexEncodedPublicKey, apiDatabase).getMessages().get()
            // TODO: Process envelopes
            Result.success()
        } catch (exception: Exception) {
            Result.failure()
        }
    }
}