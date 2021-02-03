package org.session.libsignal.service.loki.api

import nl.komponents.kovenant.*
import nl.komponents.kovenant.functional.bind
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol
import java.security.SecureRandom
import java.util.*

private class PromiseCanceledException : Exception("Promise canceled.")

class Poller(public var userPublicKey: String, private val database: LokiAPIDatabaseProtocol, private val onMessagesReceived: (List<SignalServiceProtos.Envelope>) -> Unit) {
    private var hasStarted: Boolean = false
    private val usedSnodes: MutableSet<Snode> = mutableSetOf()
    public var isCaughtUp = false

    // region Settings
    companion object {
        private val retryInterval: Long = 1 * 1000
    }
    // endregion

    // region Public API
    fun startIfNeeded() {
        if (hasStarted) { return }
        Log.d("Loki", "Started polling.")
        hasStarted = true
        setUpPolling()
    }

    fun stopIfNeeded() {
        Log.d("Loki", "Stopped polling.")
        hasStarted = false
        usedSnodes.clear()
    }
    // endregion

    // region Private API
    private fun setUpPolling() {
        if (!hasStarted) { return; }
        val thread = Thread.currentThread()
        SwarmAPI.shared.getSwarm(userPublicKey).bind(SnodeAPI.messagePollingContext) {
            usedSnodes.clear()
            val deferred = deferred<Unit, Exception>(SnodeAPI.messagePollingContext)
            pollNextSnode(deferred)
            deferred.promise
        }.always {
            Timer().schedule(object : TimerTask() {

                override fun run() {
                    thread.run { setUpPolling() }
                }
            }, retryInterval)
        }
    }

    private fun pollNextSnode(deferred: Deferred<Unit, Exception>) {
        val swarm = database.getSwarm(userPublicKey) ?: setOf()
        val unusedSnodes = swarm.subtract(usedSnodes)
        if (unusedSnodes.isNotEmpty()) {
            val index = SecureRandom().nextInt(unusedSnodes.size)
            val nextSnode = unusedSnodes.elementAt(index)
            usedSnodes.add(nextSnode)
            Log.d("Loki", "Polling $nextSnode.")
            poll(nextSnode, deferred).fail { exception ->
                if (exception is PromiseCanceledException) {
                    Log.d("Loki", "Polling $nextSnode canceled.")
                } else {
                    Log.d("Loki", "Polling $nextSnode failed; dropping it and switching to next snode.")
                    SwarmAPI.shared.dropSnodeFromSwarmIfNeeded(nextSnode, userPublicKey)
                    pollNextSnode(deferred)
                }
            }
        } else {
            isCaughtUp = true
            deferred.resolve()
        }
    }

    private fun poll(snode: Snode, deferred: Deferred<Unit, Exception>): Promise<Unit, Exception> {
        if (!hasStarted) { return Promise.ofFail(PromiseCanceledException()) }
        return SnodeAPI.shared.getRawMessages(snode, userPublicKey).bind(SnodeAPI.messagePollingContext) { rawResponse ->
            isCaughtUp = true
            if (deferred.promise.isDone()) {
                task { Unit } // The long polling connection has been canceled; don't recurse
            } else {
                val messages = SnodeAPI.shared.parseRawMessagesResponse(rawResponse, snode, userPublicKey)
                onMessagesReceived(messages)
                poll(snode, deferred)
            }
        }
    }
    // endregion
}
