package org.thoughtcrime.securesms.net

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.TextSecurePreferences

class UnregisteredInterceptor(val context: Context) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val response = chain.proceed(chain.request())
    if (response.code() == 403) {
      TextSecurePreferences.setUnauthorizedReceived(context, true)
    }
    return response
  }

  companion object {
    val TAG = Log.tag(UnregisteredInterceptor::class.java)
  }
}
