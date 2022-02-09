package org.thoughtcrime.securesms.net;

import android.os.Build;

import org.thoughtcrime.securesms.BuildConfig;

/**
 * The user agent that should be used by default -- includes app name, version, etc.
 */
public class StandardUserAgentInterceptor extends UserAgentInterceptor {

  public static final String USER_AGENT = "Signal-Android/" + BuildConfig.VERSION_NAME + " Android/" + Build.VERSION.SDK_INT;

  public StandardUserAgentInterceptor() {
    super(USER_AGENT);
  }
}
