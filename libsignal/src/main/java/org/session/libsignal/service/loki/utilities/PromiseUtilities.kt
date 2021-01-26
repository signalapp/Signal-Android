@file:JvmName("PromiseUtilities")
package org.session.libsignal.service.loki.utilities

import nl.komponents.kovenant.*
import nl.komponents.kovenant.jvm.asDispatcher
import org.session.libsignal.libsignal.logging.Log
import java.util.concurrent.Executors

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
