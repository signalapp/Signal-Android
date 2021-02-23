package org.thoughtcrime.securesms.loki.api

import android.content.Context
import android.os.Handler
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.utilities.successBackground
import org.session.libsignal.service.api.messages.SignalServiceEnvelope
import org.session.libsignal.service.loki.api.SnodeAPI
import org.session.libsignal.service.loki.api.SwarmAPI
import org.session.libsignal.service.loki.utilities.getRandomElementOrNull
import org.thoughtcrime.securesms.database.DatabaseFactory

class ClosedGroupPoller private constructor(private val context: Context) {
    private var isPolling = false
    private val handler: Handler by lazy { Handler() }

    private val task = object : Runnable {

        override fun run() {
            poll()
            handler.postDelayed(this, pollInterval)
        }
    }

    // region Settings
    companion object {
        private val pollInterval: Long = 4 * 1000

        public lateinit var shared: ClosedGroupPoller

        public fun configureIfNeeded(context: Context) {
            if (::shared.isInitialized) { return; }
            shared = ClosedGroupPoller(context)
        }
    }
    // endregion

    // region Error
    class InsufficientSnodesException() : Exception("No snodes left to poll.")
    class PollingCanceledException() : Exception("Polling canceled.")
    // endregion

    // region Public API
    fun startIfNeeded() {
        if (isPolling) { return }
        isPolling = true
        task.run()
    }

    fun pollOnce(): List<Promise<Unit, Exception>> {
        if (isPolling) { return listOf() }
        isPolling = true
        return poll()
    }

    fun stopIfNeeded() {
        isPolling = false
        handler.removeCallbacks(task)
    }
    // endregion

    // region Private API
    private fun poll(): List<Promise<Unit, Exception>> {
        if (!isPolling) { return listOf() }
        val publicKeys = DatabaseFactory.getLokiAPIDatabase(context).getAllClosedGroupPublicKeys()
        return publicKeys.map { publicKey ->
            val promise = SwarmAPI.shared.getSwarm(publicKey).bind { swarm ->
                val snode = swarm.getRandomElementOrNull() ?: throw InsufficientSnodesException() // Should be cryptographically secure
                if (!isPolling) { throw PollingCanceledException() }
                SnodeAPI.shared.getRawMessages(snode, publicKey).map {SnodeAPI.shared.parseRawMessagesResponse(it, snode, publicKey) }
            }
            promise.successBackground { messages ->
                if (messages.isNotEmpty()) {
                    Log.d("Loki", "Received ${messages.count()} new message(s) in closed group with public key: $publicKey.")
                }
                messages.forEach {
                    PushContentReceiveJob(context).processEnvelope(SignalServiceEnvelope(it), false)
                }
            }
            promise.fail {
                Log.d("Loki", "Polling failed for closed group with public key: $publicKey due to error: $it.")
            }
            promise.map { Unit }
        }
    }
    // endregion
}
