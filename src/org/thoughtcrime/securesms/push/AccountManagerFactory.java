package org.thoughtcrime.securesms.push;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.push.SignalServiceUrl;

public class AccountManagerFactory {

  public static SignalServiceAccountManager createManager(Context context) {
    return new SignalServiceAccountManager(getUrl(context), getTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  public static SignalServiceAccountManager createManager(Context context, String number, String password) {
    return new SignalServiceAccountManager(getUrl(number), getTrustStore(context, number),
                                           number, password, BuildConfig.USER_AGENT);
  }

  private static SignalServiceUrl getUrl(@NonNull Context context) {
    return getUrl(TextSecurePreferences.getLocalNumber(context));
  }

  private static TrustStore getTrustStore(@NonNull Context context) {
    return getTrustStore(context, TextSecurePreferences.getLocalNumber(context));
  }

  private static SignalServiceUrl getUrl(@NonNull String number) {
    if (Censorship.isCensored(number)) {
      return new SignalServiceUrl(BuildConfig.UNCENSORED_FRONTING_HOST, BuildConfig.CENSORED_REFLECTOR);
    } else {
      return new SignalServiceUrl(BuildConfig.TEXTSECURE_URL, null);
    }
  }

  private static TrustStore getTrustStore(@NonNull Context context, @NonNull String number) {
    if (Censorship.isCensored(number)) {
      return new CensorshipFrontingTrustStore(context);
    } else {
      return new SignalServiceTrustStore(context);
    }
  }

}
