package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

public class TextSecureCommunicationFactory {

  public static SignalServiceAccountManager createManager(Context context) {
    return new SignalServiceAccountManager(BuildConfig.TEXTSECURE_URL,
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  public static SignalServiceAccountManager createManager(Context context, String number, String password) {
    return new SignalServiceAccountManager(BuildConfig.TEXTSECURE_URL, new TextSecurePushTrustStore(context),
                                           number, password, BuildConfig.USER_AGENT);
  }

}
