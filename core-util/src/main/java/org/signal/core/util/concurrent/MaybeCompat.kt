/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.concurrent

import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.exceptions.Exceptions
import io.reactivex.rxjava3.plugins.RxJavaPlugins

/**
 * Kotlin 1.8 started respecting RxJava nullability annotations but RxJava has some oddities where it breaks those rules.
 * This essentially re-implements [Maybe.fromCallable] with an emitter so we don't have to do it everywhere ourselves.
 */
object MaybeCompat {
  fun <T : Any> fromCallable(callable: () -> T?): Maybe<T> {
    return Maybe.create { emitter ->
      val result = try {
        callable()
      } catch (e: Throwable) {
        Exceptions.throwIfFatal(e)
        if (!emitter.isDisposed) {
          emitter.onError(e)
        } else {
          RxJavaPlugins.onError(e)
        }
        return@create
      }

      if (!emitter.isDisposed) {
        if (result == null) {
          emitter.onComplete()
        } else {
          emitter.onSuccess(result)
        }
      }
    }
  }
}
