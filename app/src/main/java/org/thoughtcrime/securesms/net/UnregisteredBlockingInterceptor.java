package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Blocks network access when device is unregistered.
 */
public final class UnregisteredBlockingInterceptor implements Interceptor {

  private static final String TAG = Log.tag(UnregisteredBlockingInterceptor.class);

  @Override
  public @NonNull Response intercept(@NonNull Chain chain) throws IOException {
    if (TextSecurePreferences.isUnauthorizedRecieved(ApplicationDependencies.getApplication()) &&
        PushServiceSocket.isNotRegistrationPath(chain.request().url().encodedPath()))
    {
      Log.w(TAG, "Preventing request because device is unregistered.");
      return new Response.Builder().request(chain.request())
                                   .protocol(Protocol.HTTP_1_1)
                                   .receivedResponseAtMillis(System.currentTimeMillis())
                                   .message("")
                                   .body(ResponseBody.create(null, ""))
                                   .code(508)
                                   .build();
    }

    return chain.proceed(chain.request());
  }
}
