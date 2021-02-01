@file:JvmName("PromiseUtilities")
package org.session.libsignal.utilities

import nl.komponents.kovenant.Context
import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.jvm.asDispatcher
import org.session.libsignal.libsignal.logging.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

fun Kovenant.createContext(): Context {
    return createContext {
        callbackContext.dispatcher = Executors.newSingleThreadExecutor().asDispatcher()
        workerContext.dispatcher = ThreadUtils.executorPool.asDispatcher()
        multipleCompletion = { v1, v2 ->
            Log.d("Loki", "Promise resolved more than once (first with $v1, then with $v2); ignoring $v2.")
        }
    }
}

fun <V, E : Throwable> Promise<V, E>.get(defaultValue: V): V {
    return try {
        get()
    } catch (e: Exception) {
        defaultValue
    }
}

fun <V, E : Throwable> Promise<V, E>.recover(callback: (exception: E) -> V): Promise<V, E> {
    val deferred = deferred<V, E>()
    success {
        deferred.resolve(it)
    }.fail {
        try {
            val value = callback(it)
            deferred.resolve(value)
        } catch (e: Throwable) {
            deferred.reject(it)
        }
    }
    return deferred.promise
}

fun <V, E> Promise<V, E>.successBackground(callback: (value: V) -> Unit): Promise<V, E> {
    ThreadUtils.queue {
        try {
            callback(get())
        } catch (e: Exception) {
            Log.d("Loki", "Failed to execute task in background: ${e.message}.")
        }
    }
    return this
}

fun <V> Promise<V, Exception>.timeout(millis: Long): Promise<V, Exception> {
    if (this.isDone()) { return this; }
    val deferred = deferred<V, Exception>()
    ThreadUtils.queue {
        Thread.sleep(millis)
        if (!deferred.promise.isDone()) {
            deferred.reject(TimeoutException("Promise timed out."))
        }
    }
    this.success {
        if (!deferred.promise.isDone()) { deferred.resolve(it) }
    }.fail {
        if (!deferred.promise.isDone()) { deferred.reject(it) }
    }
    return deferred.promise
}