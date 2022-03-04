package org.thoughtcrime.securesms.gcm;

import androidx.annotation.WorkerThread

import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging

import org.signal.core.util.logging.Log
import org.whispersystems.libsignal.util.guava.Optional

import java.io.IOException
import java.util.concurrent.ExecutionException

object FcmUtil {

  private val TAG: String = Log.tag(FcmUtil.javaClass)

  /**
   * Retrieves the current FCM token. If one isn't available, it'll be generated.
   */
  @JvmStatic
  @WorkerThread
  @Throws(FCMDisabledException::class)
  fun getToken(): Optional<String> {
    var token: String? = null

    try {
      token = Tasks.await(FirebaseMessaging.getInstance().token)
    } catch (e: InterruptedException) {
      Log.w(TAG, "Was interrupted while waiting for the token.")
    } catch (e: IOException) { // IGNORE THIS WARNING
      throw FCMDisabledException()
    } catch (e: ExecutionException) {
      Log.w(TAG, "Failed to get the token.", e.cause)
    }


    return Optional.fromNullable(token?.takeIf { token.isNotEmpty() })
  }

  /**
   * MicroG can disable FCM.
   *
   * They do this by throwing an [IOException] with a message of SERVICE_NOT_AVAILABLE.
   *
   * To respond properly, set [AccountValues.fcmEnabled] to false and not use FCM again.
   */
  class FCMDisabledException : Exception("MicroG disabled this functionality")
}
