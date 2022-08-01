@file:JvmName("RxExtensions")

package org.signal.core.util.concurrent

import android.annotation.SuppressLint
import io.reactivex.rxjava3.core.Single
import java.lang.RuntimeException

/**
 * Throw an [InterruptedException] if a [Single.blockingGet] call is interrupted. This can
 * happen when being called by code already within an Rx chain that is disposed.
 *
 * [Single.blockingGet] is considered harmful and should not be used.
 */
@SuppressLint("UnsafeBlockingGet")
@Throws(InterruptedException::class)
fun <T : Any> Single<T>.safeBlockingGet(): T {
  try {
    return blockingGet()
  } catch (e: RuntimeException) {
    val cause = e.cause
    if (cause is InterruptedException) {
      throw cause
    } else {
      throw e
    }
  }
}
