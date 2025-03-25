package org.thoughtcrime.securesms.net

import okhttp3.Interceptor
import okhttp3.Response
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.keyvalue.SignalStore.Companion.misc
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import java.io.IOException

/**
 * Marks the client as remotely-deprecated when it receives a 499 response.
 */
class RemoteDeprecationDetectorInterceptor(private val getConfiguration: () -> SignalServiceConfiguration) : Interceptor {

  companion object {
    private val TAG = tag(RemoteDeprecationDetectorInterceptor::class.java)
  }

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val response = chain.proceed(request)

    if (response.code == 499 && !misc.isClientDeprecated && getConfiguration().signalServiceUrls.any { request.url.toString().startsWith(it.url) && it.hostHeader.orNull() == request.header("host") }) {
      Log.w(TAG, "Received 499. Client version is deprecated.", true)
      misc.isClientDeprecated = true
    }

    return response
  }
}
