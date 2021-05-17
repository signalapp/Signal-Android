package org.session.libsession.messaging.sending_receiving.pollers

import android.os.Handler
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.crypto.getRandomElementOrNull
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.successBackground

class ClosedGroupPoller {
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
        val publicKeys = MessagingModuleConfiguration.shared.storage.getAllActiveClosedGroupPublicKeys()
        return publicKeys.map { publicKey ->
            val promise = SnodeAPI.getSwarm(publicKey).bind { swarm ->
                val snode = swarm.getRandomElementOrNull() ?: throw InsufficientSnodesException() // Should be cryptographically secure
                if (!isPolling) { throw PollingCanceledException() }
                SnodeAPI.getRawMessages(snode, publicKey).map {SnodeAPI.parseRawMessagesResponse(it, snode, publicKey) }
            }
            promise.successBackground { messages ->
                if (!MessagingModuleConfiguration.shared.storage.isGroupActive(publicKey)) {
                    // ignore inactive group's messages
                    return@successBackground
                }
                messages.forEach { envelope ->
                    val job = MessageReceiveJob(envelope.toByteArray(), false)
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
