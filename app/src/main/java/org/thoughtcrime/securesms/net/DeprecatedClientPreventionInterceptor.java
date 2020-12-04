package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Disallows network requests when your client has been deprecated. When the client is deprecated,
 * we simply fake a 499 response.
 */
public final class DeprecatedClientPreventionInterceptor implements Interceptor {

  private static final String TAG = Log.tag(DeprecatedClientPreventionInterceptor.class);

  @Override
  public @NonNull Response intercept(@NonNull Chain chain) throws IOException {
    if (SignalStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Preventing request because client is deprecated.");
      return new Response.Builder()
                         .request(chain.request())
                         .protocol(Protocol.HTTP_1_1)
                         .receivedResponseAtMillis(System.currentTimeMillis())
                         .message("")
                         .body(ResponseBody.create(null, ""))
                         .code(499)
                         .build();
    } else {
      return chain.proceed(chain.request());
    }
  }
}
