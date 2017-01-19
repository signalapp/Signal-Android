package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.thoughtcrime.redphone.signaling.RedPhoneAccountManager;
import org.thoughtcrime.redphone.signaling.RedPhoneTrustStore;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.jobs.GcmRefreshJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {GcmRefreshJob.class,
                                     RefreshAttributesJob.class})
public class RedPhoneCommunicationModule {

  private final Context context;

  public RedPhoneCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides RedPhoneAccountManager provideRedPhoneAccountManager() {
    return new RedPhoneAccountManager(BuildConfig.REDPHONE_MASTER_URL,
                                      new RedPhoneTrustStore(context),
                                      TextSecurePreferences.getLocalNumber(context),
                                      TextSecurePreferences.getPushServerPassword(context));
  }

}
