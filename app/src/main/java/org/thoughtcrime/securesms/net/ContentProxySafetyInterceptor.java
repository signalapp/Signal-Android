package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.logging.Log;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Interceptor to do extra safety checks on requests through the {@link ContentProxySelector}
 * to prevent non-whitelisted requests from getting to it. In particular, this guards against
 * requests redirecting to non-whitelisted domains.
 *
 * Note that because of the way interceptors are ordered, OkHttp will hit the proxy with the
 * bad-redirected-domain before we can intercept the request, so we have to "look ahead" by
 * detecting a redirected response on the first pass.
 */
public class ContentProxySafetyInterceptor implements Interceptor {

  private static final String TAG = Log.tag(ContentProxySafetyInterceptor.class);

  @Override
  public @NonNull Response intercept(@NonNull Chain chain) throws IOException {
    if (isWhitelisted(chain.request().url())) {
      Response response = chain.proceed(chain.request());

      if (response.isRedirect()) {
        if (isWhitelisted(response.header("Location"))) {
          return response;
        } else {
          Log.w(TAG, "Tried to redirect to a non-whitelisted domain!");
          chain.call().cancel();
          throw new IOException("Tried to redirect to a non-whitelisted domain!");
        }
      } else {
        return response;
      }
    } else {
      Log.w(TAG, "Request was for a non-whitelisted domain!");
      chain.call().cancel();
      throw new IOException("Request was for a non-whitelisted domain!");
    }
  }

  private static boolean isWhitelisted(@NonNull HttpUrl url) {
    return isWhitelisted(url.toString());
  }

  private static boolean isWhitelisted(@Nullable String url) {
    return LinkPreviewUtil.isWhitelistedLinkUrl(url) || LinkPreviewUtil.isWhitelistedMediaUrl(url);
  }
}
