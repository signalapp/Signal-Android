package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

import network.loki.messenger.BuildConfig;

public class AccountManagerFactory {

  private static final String TAG = AccountManagerFactory.class.getSimpleName();

  public static SignalServiceAccountManager createManager(Context context) {
    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  public static SignalServiceAccountManager createManager(final Context context, String number, String password) {
    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(number),
                                           number, password, BuildConfig.USER_AGENT);
  }

}
