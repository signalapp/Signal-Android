package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.crypto.getRandomElementOrNull
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.successBackground
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

class ClosedGroupPollerV2 {
    private val executorService = Executors.newScheduledThreadPool(4)
    private var isPolling = mutableMapOf<String, Boolean>()
    private var futures = mutableMapOf<String, ScheduledFuture<*>>()

    private fun isPolling(groupPublicKey: String): Boolean {
        return isPolling[groupPublicKey] ?: false
    }

    companion object {
        private val minPollInterval = 4 * 1000
        private val maxPollInterval = 4 * 60 * 1000

        @JvmStatic
        val shared = ClosedGroupPollerV2()
    }

    class InsufficientSnodesException() : Exception("No snodes left to poll.")
    class PollingCanceledException() : Exception("Polling canceled.")

    fun start() {
        val storage = MessagingModuleConfiguration.shared.storage
        val allGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
        allGroupPublicKeys.forEach { startPolling(it) }
    }

    fun startPolling(groupPublicKey: String) {
        if (isPolling(groupPublicKey)) { return }
        isPolling[groupPublicKey] = true
        setUpPolling(groupPublicKey)
    }

    fun stop() {
        val storage = MessagingModuleConfiguration.shared.storage
        val allGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
        allGroupPublicKeys.forEach { stopPolling(it) }
    }

    fun stopPolling(groupPublicKey: String) {
        futures[groupPublicKey]?.cancel(false)
        isPolling[groupPublicKey] = false
    }

    private fun setUpPolling(groupPublicKey: String) {
        poll(groupPublicKey).success {
            pollRecursively(groupPublicKey)
        }.fail {
            // The error is logged in poll(_:)
            pollRecursively(groupPublicKey)
        }
    }

    private fun pollRecursively(groupPublicKey: String) {
        if (!isPolling(groupPublicKey)) { return }
        // Get the received date of the last message in the thread. If we don't have any messages yet, pick some
        // reasonable fake time interval to use instead.
        val storage = MessagingModuleConfiguration.shared.storage
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val threadID = storage.getThreadId(groupID) ?: return
        val lastUpdated = storage.getLastUpdated(threadID)
        val timeSinceLastMessage = if (lastUpdated != -1L) Date().time - lastUpdated else 5 * 60 * 1000
        val minPollInterval = Companion.minPollInterval
        val limit: Long = 12 * 60 * 60 * 1000
        val a = (Companion.maxPollInterval - minPollInterval).toDouble() / limit.toDouble()
        val nextPollInterval = a * min(timeSinceLastMessage, limit) + minPollInterval
        Log.d("Loki", "Next poll interval for closed group with public key: $groupPublicKey is ${nextPollInterval / 1000} s.")
        executorService?.schedule({
            poll(groupPublicKey).success {
                pollRecursively(groupPublicKey)
            }.fail {
                // The error is logged in poll(_:)
                pollRecursively(groupPublicKey)
            }
        }, nextPollInterval.toLong(), TimeUnit.MILLISECONDS)
    }

    fun poll(groupPublicKey: String): Promise<Unit, Exception> {
        if (!isPolling(groupPublicKey)) { return Promise.of(Unit) }
        val promise = SnodeAPI.getSwarm(groupPublicKey).bind { swarm ->
            val snode = swarm.getRandomElementOrNull() ?: throw InsufficientSnodesException() // Should be cryptographically secure
            if (!isPolling(groupPublicKey)) { throw PollingCanceledException() }
            SnodeAPI.getRawMessages(snode, groupPublicKey).map { SnodeAPI.parseRawMessagesResponse(it, snode, groupPublicKey) }
        }
        promise.success { envelopes ->
            if (!isPolling(groupPublicKey)) { return@success }
            envelopes.forEach { envelope ->
                val job = MessageReceiveJob(envelope.toByteArray())
                JobQueue.shared.add(job)
            }
        }
        promise.fail {
            Log.d("Loki", "Polling failed for closed group with public key: $groupPublicKey due to error: $it.")
        }
        return promise.map { }
    }
}
