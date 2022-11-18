package org.thoughtcrime.securesms.components.webrtc

import android.opengl.EGL14
import org.signal.core.util.logging.Log
import org.webrtc.EglBase
import org.webrtc.EglBase10
import org.webrtc.EglBase14
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import javax.microedition.khronos.egl.EGL10
import kotlin.concurrent.withLock

private val TAG = Log.tag(EglBaseWrapper::class.java)

/**
 * Wrapper which allows caller to perform synchronized actions on an EglBase object.
 */
class EglBaseWrapper(val eglBase: EglBase?) {

  private val lock: Lock = ReentrantLock()

  fun require(): EglBase = requireNotNull(eglBase)

  @Volatile
  private var isReleased: Boolean = false

  fun performWithValidEglBase(consumer: Consumer<EglBase>) {
    if (isReleased) {
      Log.d(TAG, "Tried to use a released EglBase", Exception())
      return
    }

    if (eglBase == null) {
      return
    }

    lock.withLock {
      if (isReleased) {
        Log.d(TAG, "Tried to use a released EglBase", Exception())
        return
      }

      val hasSharedContext = when (val context: EglBase.Context = eglBase.eglBaseContext) {
        is EglBase14.Context -> context.rawContext != EGL14.EGL_NO_CONTEXT
        is EglBase10.Context -> context.rawContext != EGL10.EGL_NO_CONTEXT
        else -> throw IllegalStateException("Unknown context")
      }

      if (hasSharedContext) {
        consumer.accept(eglBase)
      }
    }
  }

  fun releaseEglBase() {
    if (isReleased || eglBase == null) {
      return
    }

    lock.withLock {
      if (isReleased) {
        return
      }

      isReleased = true
      eglBase.release()
    }
  }
}
