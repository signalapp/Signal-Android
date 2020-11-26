@file:JvmName("PromiseUtilities")
package org.session.libsignal.service.loki.utilities

import nl.komponents.kovenant.*
import org.session.libsignal.libsignal.logging.Log
import kotlin.math.max

// Try to use all available threads minus one for the callback
private val recommendedThreadCount: Int
    get() = Runtime.getRuntime().availableProcessors() - 1

fun Kovenant.createContext(contextName: String, threadCount: Int = max(recommendedThreadCount, 1)): Context {
    return createContext {
        callbackContext.dispatcher = buildDispatcher {
            name = "${contextName}CallbackDispatcher"
            // Ref: http://kovenant.komponents.nl/api/core_usage/#execution-order
            // Having 1 concurrent task ensures we have in-order callback handling
            concurrentTasks = 1
        }
        workerContext.dispatcher = buildDispatcher {
            name = "${contextName}WorkerDispatcher"
            concurrentTasks = threadCount
        }
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
