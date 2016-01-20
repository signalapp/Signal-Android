package org.privatechats.securesms.dependencies;

import android.content.Context;

import org.privatechats.redphone.signaling.RedPhoneAccountManager;
import org.privatechats.redphone.signaling.RedPhoneTrustStore;
import org.privatechats.securesms.BuildConfig;
import org.privatechats.securesms.jobs.GcmRefreshJob;
import org.privatechats.securesms.jobs.RefreshAttributesJob;
import org.privatechats.securesms.util.TextSecurePreferences;

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
