package org.session.libsession.messaging.sending_receiving.pollers

import android.os.Handler
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.utilities.successBackground

import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.utilities.Base64
import org.session.libsignal.service.loki.utilities.getRandomElementOrNull

class ClosedGroupPoller {
    private var isPolling = false
    private val handler: Handler by lazy { Handler() }

    private val task = object : Runnable {

        override fun run() {
            poll()
            handler.postDelayed(this, ClosedGroupPoller.pollInterval)
        }
    }

    // region Settings
    companion object {
        private val pollInterval: Long = 2 * 1000
    }
    // endregion

    // region Error
    class InsufficientSnodesException() : Exception("No snodes left to poll.")
    class PollingCanceledException() : Exception("Polling canceled.")
    // endregion

    // region Public API
    public fun startIfNeeded() {
        if (isPolling) { return }
        isPolling = true
        task.run()
    }

    public fun pollOnce(): List<Promise<Unit, Exception>> {
        if (isPolling) { return listOf() }
        isPolling = true
        return poll()
    }

    public fun stopIfNeeded() {
        isPolling = false
        handler.removeCallbacks(task)
    }
    // endregion

    // region Private API
    private fun poll(): List<Promise<Unit, Exception>> {
        if (!isPolling) { return listOf() }
        val publicKeys = MessagingConfiguration.shared.sskDatabase.getAllClosedGroupPublicKeys()
        return publicKeys.map { publicKey ->
            val promise = SnodeAPI.getSwarm(publicKey).bind { swarm ->
                val snode = swarm.getRandomElementOrNull() ?: throw InsufficientSnodesException() // Should be cryptographically secure
                if (!isPolling) { throw PollingCanceledException() }
                SnodeAPI.getRawMessages(snode, publicKey).map {SnodeAPI.parseRawMessagesResponse(it, snode, publicKey) }
            }
            promise.successBackground { messages ->
                if (messages.isNotEmpty()) {
                    Log.d("Loki", "Received ${messages.count()} new message(s) in closed group with public key: $publicKey.")
                }
                messages.forEach { message ->
                    val rawMessageAsJSON = message as? Map<*, *>
                    val base64EncodedData = rawMessageAsJSON?.get("data") as? String
                    val data = base64EncodedData?.let { Base64.decode(it) } ?: return@forEach
                    val job = MessageReceiveJob(MessageWrapper.unwrap(data), false)
                    JobQueue.shared.add(job)
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
