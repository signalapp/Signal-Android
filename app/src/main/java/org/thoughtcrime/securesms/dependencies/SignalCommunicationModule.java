package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.SignalServiceAccountManager;
import org.session.libsignal.service.api.SignalServiceMessageReceiver;
import org.session.libsignal.service.api.SignalServiceMessageSender;
import org.session.libsignal.service.api.util.CredentialsProvider;
import org.session.libsignal.service.api.util.SleepTimer;
import org.session.libsignal.service.api.util.UptimeSleepTimer;
import org.session.libsignal.service.api.websocket.ConnectivityListener;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob;
import org.thoughtcrime.securesms.jobs.AvatarDownloadJob;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushGroupUpdateJob;
import org.thoughtcrime.securesms.jobs.PushMediaSendJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.jobs.PushTextSendJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
import org.thoughtcrime.securesms.jobs.RequestGroupInfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.jobs.RotateCertificateJob;
import org.thoughtcrime.securesms.jobs.RotateProfileKeyJob;
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.jobs.StickerDownloadJob;
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.protocol.SessionResetImplementation;
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment;
import org.thoughtcrime.securesms.push.MessageSenderEventListener;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.stickers.StickerPackPreviewRepository;
import org.thoughtcrime.securesms.stickers.StickerRemoteUriLoader;
import org.thoughtcrime.securesms.util.RealtimeSleepTimer;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import dagger.Module;
import dagger.Provides;
import network.loki.messenger.BuildConfig;

@Module(complete = false, injects = {PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     IncomingMessageObserver.class,
                                     PushNotificationReceiveJob.class,
                                     RefreshAttributesJob.class,
                                     RequestGroupInfoJob.class,
                                     PushGroupUpdateJob.class,
                                     AvatarDownloadJob.class,
                                     RetrieveProfileAvatarJob.class,
                                     SendReadReceiptJob.class,
                                     AppProtectionPreferenceFragment.class,
                                     RotateCertificateJob.class,
                                     SendDeliveryReceiptJob.class,
                                     RotateProfileKeyJob.class,
                                     RefreshUnidentifiedDeliveryAbilityJob.class,
                                     TypingSendJob.class,
                                     AttachmentUploadJob.class,
                                     PushDecryptJob.class,
                                     StickerDownloadJob.class,
                                     StickerPackPreviewRepository.class,
                                     StickerRemoteUriLoader.Factory.class,
                                     StickerPackDownloadJob.class,
                                     LinkPreviewRepository.class})

public class SignalCommunicationModule {

  private static final String TAG = SignalCommunicationModule.class.getSimpleName();

  private final Context                      context;
  private final SignalServiceNetworkAccess   networkAccess;

  private SignalServiceAccountManager  accountManager;
  private SignalServiceMessageSender   messageSender;
  private SignalServiceMessageReceiver messageReceiver;

  public SignalCommunicationModule(Context context, SignalServiceNetworkAccess networkAccess) {
    this.context       = context;
    this.networkAccess = networkAccess;

  }

  @Provides
  synchronized SignalServiceAccountManager provideSignalAccountManager() {
    if (this.accountManager == null) {
      this.accountManager = new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                                            new DynamicCredentialsProvider(context),
                                                            BuildConfig.USER_AGENT);
    }

    return this.accountManager;
  }

  @Provides
  public synchronized SignalServiceMessageSender provideSignalMessageSender() {
    if (this.messageSender == null) {
      this.messageSender = new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                                          new DynamicCredentialsProvider(context),
                                                          new SignalProtocolStoreImpl(context),
                                                          BuildConfig.USER_AGENT,
                                                          TextSecurePreferences.isMultiDevice(context),
                                                          Optional.fromNullable(IncomingMessageObserver.getPipe()),
                                                          Optional.fromNullable(IncomingMessageObserver.getUnidentifiedPipe()),
                                                          Optional.of(new MessageSenderEventListener(context)),
                                                          TextSecurePreferences.getLocalNumber(context),
                                                          DatabaseFactory.getLokiAPIDatabase(context),
                                                          DatabaseFactory.getSSKDatabase(context),
                                                          DatabaseFactory.getLokiThreadDatabase(context),
                                                          DatabaseFactory.getLokiMessageDatabase(context),
//                                                          DatabaseFactory.getLokiPreKeyBundleDatabase(context),
                                                          null,
                                                          new SessionResetImplementation(context),
                                                          DatabaseFactory.getLokiUserDatabase(context),
                                                          DatabaseFactory.getGroupDatabase(context),
                                                          ((ApplicationContext)context.getApplicationContext()).broadcaster);
    } else {
      this.messageSender.setMessagePipe(IncomingMessageObserver.getPipe(), IncomingMessageObserver.getUnidentifiedPipe());
      this.messageSender.setIsMultiDevice(TextSecurePreferences.isMultiDevice(context));
    }

    return this.messageSender;
  }

  @Provides
  synchronized SignalServiceMessageReceiver provideSignalMessageReceiver() {
    if (this.messageReceiver == null) {
      SleepTimer sleepTimer =  TextSecurePreferences.isFcmDisabled(context) ? new RealtimeSleepTimer(context) : new UptimeSleepTimer();

      this.messageReceiver = new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                                              new DynamicCredentialsProvider(context),
                                                              BuildConfig.USER_AGENT,
                                                              new PipeConnectivityListener(),
                                                              sleepTimer);
    }

    return this.messageReceiver;
  }

  @Provides
  synchronized SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return networkAccess;
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

  private class PipeConnectivityListener implements ConnectivityListener {

    @Override
    public void onConnected() {
      Log.i(TAG, "onConnected()");
    }

    @Override
    public void onConnecting() {
      Log.i(TAG, "onConnecting()");
    }

    @Override
    public void onDisconnected() {
      Log.w(TAG, "onDisconnected()");
    }

    @Override
    public void onAuthenticationFailure() {
      Log.w(TAG, "onAuthenticationFailure()");
      TextSecurePreferences.setUnauthorizedReceived(context, true);
    }

  }

}
