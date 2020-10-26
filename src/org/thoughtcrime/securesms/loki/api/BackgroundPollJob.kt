package org.thoughtcrime.securesms.loki.api

import android.content.Context
import kotlinx.coroutines.awaitAll
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.dependencies.InjectableType
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob
import org.thoughtcrime.securesms.jobs.RotateCertificateJob
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.loki.api.SnodeAPI
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BackgroundPollJob private constructor(parameters: Parameters) : BaseJob(parameters) {

    companion object {
        const val KEY = "BackgroundPollJob"
    }

    constructor(context: Context) : this(Parameters.Builder()
        .addConstraint(NetworkConstraint.KEY)
        .setQueue(KEY)
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .build()) {
        setContext(context)
    }

    override fun serialize(): Data {
        return Data.EMPTY
    }

    override fun getFactoryKey(): String { return KEY }

    public override fun onRun() {
        try {
            Log.d("Loki", "Performing background poll.")
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)
            val promises = mutableListOf<Promise<Unit, Exception>>()
            if (!TextSecurePreferences.isUsingFCM(context)) {
                Log.d("Loki", "Not using FCM; polling for contacts and closed groups.")
                val promise = SnodeAPI.shared.getMessages(userPublicKey).map { envelopes ->
                    envelopes.forEach {
                        PushContentReceiveJob(context).processEnvelope(SignalServiceEnvelope(it), false)
                    }
                }
                promises.add(promise)
                promises.addAll(ClosedGroupPoller.shared.pollOnce())
            }
            val openGroups = DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats().map { it.value }
            for (openGroup in openGroups) {
                val poller = PublicChatPoller(context, openGroup)
                poller.stop()
                promises.add(poller.pollForNewMessages())
            }
            all(promises).get()
        } catch (exception: Exception) {
            Log.d("Loki", "Background poll failed due to error: $exception.")
        }
    }

    public override fun onShouldRetry(e: Exception): Boolean {
        return false
    }

    override fun onCanceled() { }

    class Factory : Job.Factory<BackgroundPollJob> {

        override fun create(parameters: Parameters, data: Data): BackgroundPollJob {
            return BackgroundPollJob(parameters)
        }
    }
}
