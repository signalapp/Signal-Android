@file:JvmName("RxExtensions")

package org.signal.core.util.concurrent

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
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

fun <T : Any> Flowable<T>.observe(viewLifecycleOwner: LifecycleOwner, onNext: (T) -> Unit) {
  val lifecycleDisposable = LifecycleDisposable()
  lifecycleDisposable.bindTo(viewLifecycleOwner)
  lifecycleDisposable += subscribeBy(onNext = onNext)
}

fun Completable.observe(viewLifecycleOwner: LifecycleOwner, onComplete: () -> Unit) {
  val lifecycleDisposable = LifecycleDisposable()
  lifecycleDisposable.bindTo(viewLifecycleOwner)
  lifecycleDisposable += subscribeBy(onComplete = onComplete)
}
