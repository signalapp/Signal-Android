package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.session.libsignal.service.api.SignalServiceMessageReceiver;
import org.session.libsignal.service.api.SignalServiceMessageSender;
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
import org.thoughtcrime.securesms.jobs.PushTextSendJob;
import org.thoughtcrime.securesms.jobs.RequestGroupInfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.loki.api.SessionProtocolImpl;
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment;
import org.session.libsession.utilities.TextSecurePreferences;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RequestGroupInfoJob.class,
                                     PushGroupUpdateJob.class,
                                     AvatarDownloadJob.class,
                                     RetrieveProfileAvatarJob.class,
                                     SendReadReceiptJob.class,
                                     AppProtectionPreferenceFragment.class,
                                     SendDeliveryReceiptJob.class,
                                     TypingSendJob.class,
                                     AttachmentUploadJob.class,
                                     PushDecryptJob.class,
                                     LinkPreviewRepository.class})

public class SignalCommunicationModule {

  private final Context                      context;

  private SignalServiceMessageSender   messageSender;
  private SignalServiceMessageReceiver messageReceiver;

  public SignalCommunicationModule(Context context) {
    this.context       = context;
  }

  @Provides
  public synchronized SignalServiceMessageSender provideSignalMessageSender() {
    if (this.messageSender == null) {
      this.messageSender = new SignalServiceMessageSender(new SignalProtocolStoreImpl(context),
                                                          TextSecurePreferences.getLocalNumber(context),
                                                          DatabaseFactory.getLokiAPIDatabase(context),
                                                          DatabaseFactory.getLokiThreadDatabase(context),
                                                          DatabaseFactory.getLokiMessageDatabase(context),
                                                          new SessionProtocolImpl(context),
                                                          DatabaseFactory.getLokiUserDatabase(context),
                                                          DatabaseFactory.getGroupDatabase(context),
                                                          ((ApplicationContext)context.getApplicationContext()).broadcaster);
    }

    return this.messageSender;
  }

  @Provides
  synchronized SignalServiceMessageReceiver provideSignalMessageReceiver() {
    if (this.messageReceiver == null) {
      this.messageReceiver = new SignalServiceMessageReceiver();
    }

    return this.messageReceiver;
  }
}
