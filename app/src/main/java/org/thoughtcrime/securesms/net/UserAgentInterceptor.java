package org.thoughtcrime.securesms.net;

import android.os.Build;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

public class UserAgentInterceptor implements Interceptor {

  private static final String USER_AGENT = "Signal-Android " + BuildConfig.VERSION_NAME + " (API " + Build.VERSION.SDK_INT + ")";

  @Override
  public Response intercept(@NonNull Chain chain) throws IOException {
    return chain.proceed(chain.request().newBuilder()
                                        .header("User-Agent", USER_AGENT)
                                        .build());
  }
}
