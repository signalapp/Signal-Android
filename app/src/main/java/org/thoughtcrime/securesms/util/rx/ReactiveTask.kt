package org.thoughtcrime.securesms.util.rx

import com.google.android.gms.tasks.Task
import io.reactivex.rxjava3.core.Single

/**
 * Convert a [Task] into a [Single].
 */
fun <T : Any> Task<T>.toSingle(): Single<T> {
  return Single.create { emitter ->
    addOnCompleteListener {
      if (it.isSuccessful && !emitter.isDisposed) {
        emitter.onSuccess(it.result)
      } else if (!emitter.isDisposed) {
        emitter.onError(it.exception)
      }
    }
  }
}
