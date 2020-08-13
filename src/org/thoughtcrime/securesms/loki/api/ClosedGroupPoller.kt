package org.thoughtcrime.securesms.loki.api

import android.content.Context
import android.os.Handler
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.loki.database.SharedSenderKeysDatabase
import org.thoughtcrime.securesms.loki.utilities.successBackground
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.loki.api.SnodeAPI
import org.whispersystems.signalservice.loki.api.SwarmAPI
import org.whispersystems.signalservice.loki.utilities.getRandomElementOrNull

class ClosedGroupPoller private constructor(private val context: Context, private val database: SharedSenderKeysDatabase) {
    private var isPolling = false
    private val handler = Handler()

    private val task = object : Runnable {

        override fun run() {
            poll()
            handler.postDelayed(this, ClosedGroupPoller.pollInterval)
        }
    }

    // region Settings
    companion object {
        private val pollInterval: Long = 2 * 1000

        public lateinit var shared: ClosedGroupPoller

        public fun configureIfNeeded(context: Context, sskDatabase: SharedSenderKeysDatabase) {
            if (::shared.isInitialized) { return; }
            shared = ClosedGroupPoller(context, sskDatabase)
        }
    }
    // endregion

    // region Error
    public class InsufficientSnodesException() : Exception("No snodes left to poll.")
    public class PollingCanceledException() : Exception("Polling canceled.")
    // endregion

    // region Public API
    public fun startIfNeeded() {
        if (isPolling) { return }
        isPolling = true
        task.run()
    }

    public fun stopIfNeeded() {
        isPolling = false
        handler.removeCallbacks(task)
    }
    // endregion

    // region Private API
    private fun poll() {
        if (!isPolling) { return }
        val publicKeys = database.getAllClosedGroupPublicKeys()
        publicKeys.forEach { publicKey ->
            SwarmAPI.shared.getSwarm(publicKey).bind { swarm ->
                val snode = swarm.getRandomElementOrNull() ?: throw InsufficientSnodesException() // Should be cryptographically secure
                if (!isPolling) { throw PollingCanceledException() }
                SnodeAPI.shared.getRawMessages(snode, publicKey).map {SnodeAPI.shared.parseRawMessagesResponse(it, snode, publicKey) }
            }.successBackground { messages ->
                if (messages.isNotEmpty()) {
                    Log.d("Loki", "Received ${messages.count()} new message(s) in closed group with public key: $publicKey.")
                }
                messages.forEach {
                    PushContentReceiveJob(context).processEnvelope(SignalServiceEnvelope(it), false)
                }
            }.fail {
                Log.d("Loki", "Polling failed for closed group with public key: $publicKey due to error: $it.")
            }
        }
    }
    // endregion
}
