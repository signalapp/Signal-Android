package org.thoughtcrime.securesms.net

import okhttp3.Interceptor
import okhttp3.Response
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.util.LinkUtil.isValidPreviewUrl
import java.io.IOException

/**
 * Validates redirects for link preview requests to ensure they all meet the link criteria.
 */
class LinkPreviewRedirectValidationInterceptor : Interceptor {

  companion object {
    private val TAG = tag(LinkPreviewRedirectValidationInterceptor::class)
  }

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    val url = chain.request().url.toString()

    if (!isValidPreviewUrl(url)) {
      Log.w(TAG, "Redirect target failed link preview URL validation.")
      chain.call().cancel()
      throw IOException("Redirect target is not a valid preview URL.")
    }

    return chain.proceed(chain.request())
  }
}
