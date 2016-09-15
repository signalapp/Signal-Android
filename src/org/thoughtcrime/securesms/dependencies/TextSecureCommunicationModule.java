package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.DeviceListFragment;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.CleanPreKeysJob;
import org.thoughtcrime.securesms.jobs.CreateSignedPreKeyJob;
import org.thoughtcrime.securesms.jobs.DeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.GcmRefreshJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceBlockedUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceGroupUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushMediaSendJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.jobs.PushTextSendJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.push.SecurityEventListener;
import org.thoughtcrime.securesms.push.TextSecurePushTrustStore;
import org.thoughtcrime.securesms.service.MessageRetrievalService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

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
                                     MultiDeviceReadUpdateJob.class,
                                     MultiDeviceBlockedUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmRefreshJob.class})
public class TextSecureCommunicationModule {

  private final Context context;

  public TextSecureCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides SignalServiceAccountManager provideTextSecureAccountManager() {
    return new SignalServiceAccountManager(BuildConfig.TEXTSECURE_URL,
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public SignalServiceMessageSender create() {
        return new SignalServiceMessageSender(BuildConfig.TEXTSECURE_URL,
                                              new TextSecurePushTrustStore(context),
                                              TextSecurePreferences.getLocalNumber(context),
                                              TextSecurePreferences.getPushServerPassword(context),
                                              new SignalProtocolStoreImpl(context),
                                              BuildConfig.USER_AGENT,
                                              Optional.<SignalServiceMessageSender.EventListener>of(new SecurityEventListener(context)));
      }
    };
  }

  @Provides SignalServiceMessageReceiver provideTextSecureMessageReceiver() {
    return new SignalServiceMessageReceiver(BuildConfig.TEXTSECURE_URL,
                                         new TextSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context),
                                         BuildConfig.USER_AGENT);
  }

  public static interface TextSecureMessageSenderFactory {
    public SignalServiceMessageSender create();
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
