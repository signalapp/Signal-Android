package org.thoughtcrime.securesms.push;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.security.ProviderInstaller;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

public class AccountManagerFactory {

  private static final String TAG = AccountManagerFactory.class.getName();

  public static SignalServiceAccountManager createManager(Context context) {
    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  public static SignalServiceAccountManager createManager(final Context context, String number, String password) {
    if (new SignalServiceNetworkAccess(context).isCensored(number)) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          try {
            ProviderInstaller.installIfNeeded(context);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
          return null;
        }
      }.execute();
    }

    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(number),
                                           number, password, BuildConfig.USER_AGENT);
  }

}
