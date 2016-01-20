package org.privatechats.securesms.dependencies;

import android.content.Context;

import org.privatechats.securesms.BuildConfig;
import org.privatechats.securesms.DeviceListFragment;
import org.privatechats.securesms.crypto.storage.TextSecureAxolotlStore;
import org.privatechats.securesms.jobs.AttachmentDownloadJob;
import org.privatechats.securesms.jobs.CleanPreKeysJob;
import org.privatechats.securesms.jobs.CreateSignedPreKeyJob;
import org.privatechats.securesms.jobs.DeliveryReceiptJob;
import org.privatechats.securesms.jobs.GcmRefreshJob;
import org.privatechats.securesms.jobs.MultiDeviceContactUpdateJob;
import org.privatechats.securesms.jobs.MultiDeviceGroupUpdateJob;
import org.privatechats.securesms.jobs.PushGroupSendJob;
import org.privatechats.securesms.jobs.PushMediaSendJob;
import org.privatechats.securesms.jobs.PushNotificationReceiveJob;
import org.privatechats.securesms.jobs.PushTextSendJob;
import org.privatechats.securesms.jobs.RefreshAttributesJob;
import org.privatechats.securesms.jobs.RefreshPreKeysJob;
import org.privatechats.securesms.push.SecurityEventListener;
import org.privatechats.securesms.push.TextSecurePushTrustStore;
import org.privatechats.securesms.service.MessageRetrievalService;
import org.privatechats.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     DeliveryReceiptJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmRefreshJob.class})
public class TextSecureCommunicationModule {

  private final Context context;

  public TextSecureCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides TextSecureAccountManager provideTextSecureAccountManager() {
    return new TextSecureAccountManager(BuildConfig.TEXTSECURE_URL,
                                        new TextSecurePushTrustStore(context),
                                        TextSecurePreferences.getLocalNumber(context),
                                        TextSecurePreferences.getPushServerPassword(context),
                                        BuildConfig.USER_AGENT);
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public TextSecureMessageSender create() {
        return new TextSecureMessageSender(BuildConfig.TEXTSECURE_URL,
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           new TextSecureAxolotlStore(context),
                                           BuildConfig.USER_AGENT,
                                           Optional.<TextSecureMessageSender.EventListener>of(new SecurityEventListener(context)));
      }
    };
  }

  @Provides TextSecureMessageReceiver provideTextSecureMessageReceiver() {
    return new TextSecureMessageReceiver(BuildConfig.TEXTSECURE_URL,
                                         new TextSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context),
                                         BuildConfig.USER_AGENT);
  }

  public static interface TextSecureMessageSenderFactory {
    public TextSecureMessageSender create();
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

}
