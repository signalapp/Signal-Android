@file:JvmName("PromiseUtilities")
package org.thoughtcrime.securesms.loki.utilities

import android.util.Log
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.TimeoutException

fun <V, E> Promise<V, E>.successBackground(callback: (value: V) -> Unit): Promise<V, E> {
    Thread {
        try {
            callback(get())
        } catch (e: Exception) {
            Log.d("Loki", "Failed to execute task in background: ${e.message}.")
        }
    }.start()
    return this
}

fun <V> Promise<V, Exception>.timeout(millis: Long): Promise<V, Exception> {
    if (this.isDone()) { return this; }
    val deferred = deferred<V, Exception>()
    Thread {
        Thread.sleep(millis)
        if (!deferred.promise.isDone()) {
            deferred.reject(TimeoutException("Promise timed out."))
        }
    }.start()
    this.success {
        if (!deferred.promise.isDone()) { deferred.resolve(it) }
    }.fail {
        if (!deferred.promise.isDone()) { deferred.reject(it) }
    }
    return deferred.promise
}