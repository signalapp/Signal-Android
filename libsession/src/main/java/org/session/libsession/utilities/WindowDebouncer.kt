package org.session.libsession.utilities

import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Not really a 'debouncer' but named to be similar to the current Debouncer
 * designed to queue tasks on a window (if not already queued) like a timer
 */
class WindowDebouncer(private val window: Long, private val timer: Timer) {

    private val atomicRef: AtomicReference<Runnable?> = AtomicReference(null)
    private val hasStarted = AtomicBoolean(false)

    private val recursiveRunnable: TimerTask = object:TimerTask() {
        override fun run() {
            val runnable = atomicRef.getAndSet(null)
            runnable?.run()
        }
    }

    fun publish(runnable: Runnable) {
        if (hasStarted.compareAndSet(false, true)) {
            timer.scheduleAtFixedRate(recursiveRunnable, 0, window)
        }
        atomicRef.compareAndSet(null, runnable)
    }

}