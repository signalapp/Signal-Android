package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.api.TextSecureAccountManager;

public class TextSecureCommunicationFactory {

  public static TextSecureAccountManager createManager(Context context) {
    return new TextSecureAccountManager(BuildConfig.PUSH_URL,
                                        new TextSecurePushTrustStore(context),
                                        TextSecurePreferences.getLocalNumber(context),
                                        TextSecurePreferences.getPushServerPassword(context));
  }

  public static TextSecureAccountManager createManager(Context context, String number, String password) {
    return new TextSecureAccountManager(BuildConfig.PUSH_URL, new TextSecurePushTrustStore(context),
                                        number, password);
  }

}
