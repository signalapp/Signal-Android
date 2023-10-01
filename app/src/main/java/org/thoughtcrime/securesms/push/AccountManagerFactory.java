package org.thoughtcrime.securesms.push;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.security.ProviderInstaller;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;

public class AccountManagerFactory {

  private static AccountManagerFactory instance;
  public static AccountManagerFactory getInstance() {
    if (instance == null) {
      synchronized (AccountManagerFactory.class) {
        if (instance == null) {
          instance = new AccountManagerFactory();
        }
      }
    }
    return instance;
  }

  @VisibleForTesting
  public static void setInstance(@NonNull AccountManagerFactory accountManagerFactory) {
    synchronized (AccountManagerFactory.class) {
      instance = accountManagerFactory;
    }
  }
  private static final String TAG = Log.tag(AccountManagerFactory.class);

  public @NonNull SignalServiceAccountManager createAuthenticated(@NonNull Context context,
                                                                  @NonNull ACI aci,
                                                                  @NonNull PNI pni,
                                                                  @NonNull String e164,
                                                                  int deviceId,
                                                                  @NonNull String password)
  {
    if (ApplicationDependencies.getSignalServiceNetworkAccess().isCensored(e164)) {
      SignalExecutors.BOUNDED.execute(() -> {
        try {
          ProviderInstaller.installIfNeeded(context);
        } catch (Throwable t) {
          Log.w(TAG, t);
        }
      });
    }

    return new SignalServiceAccountManager(ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration(e164),
                                           aci,
                                           pni,
                                           e164,
                                           deviceId,
                                           password,
                                           BuildConfig.SIGNAL_AGENT,
                                           FeatureFlags.okHttpAutomaticRetry(),
                                           FeatureFlags.groupLimits().getHardLimit());
  }

  /**
   * Should only be used during registration when you haven't yet been assigned an ACI.
   */
  public @NonNull SignalServiceAccountManager createUnauthenticated(@NonNull Context context,
                                                                    @NonNull String e164,
                                                                    int deviceId,
                                                                    @NonNull String password)
  {
    if (new SignalServiceNetworkAccess(context).isCensored(e164)) {
      SignalExecutors.BOUNDED.execute(() -> {
        try {
          ProviderInstaller.installIfNeeded(context);
        } catch (Throwable t) {
          Log.w(TAG, t);
        }
      });
    }

    return new SignalServiceAccountManager(ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration(e164),
                                           null,
                                           null,
                                           e164,
                                           deviceId,
                                           password,
                                           BuildConfig.SIGNAL_AGENT,
                                           FeatureFlags.okHttpAutomaticRetry(),
                                           FeatureFlags.groupLimits().getHardLimit());
  }

}
