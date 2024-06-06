package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Marks the client as remotely-deprecated when it receives a 499 response.
 */
public final class RemoteDeprecationDetectorInterceptor implements Interceptor {

  private static final String TAG = Log.tag(RemoteDeprecationDetectorInterceptor.class);

  @Override
  public @NonNull Response intercept(@NonNull Chain chain) throws IOException {
    Response response = chain.proceed(chain.request());

    if (response.code() == 499 && !SignalStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Received 499. Client version is deprecated.", true);
      SignalStore.misc().setClientDeprecated(true);
    }

    return response;
  }
}
