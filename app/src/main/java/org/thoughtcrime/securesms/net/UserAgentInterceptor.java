package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

public class UserAgentInterceptor implements Interceptor {

  private final String userAgent;

  public UserAgentInterceptor(@NonNull String userAgent) {
    this.userAgent = userAgent;
  }

  @Override
  public Response intercept(@NonNull Chain chain) throws IOException {
    return chain.proceed(chain.request().newBuilder()
                                        .header("User-Agent", userAgent)
                                        .build());
  }
}
