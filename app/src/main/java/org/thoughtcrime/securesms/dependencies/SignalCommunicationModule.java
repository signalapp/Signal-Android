package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.session.libsignal.service.api.SignalServiceMessageReceiver;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.AvatarDownloadJob;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.loki.api.SessionProtocolImpl;
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment;
import org.session.libsession.utilities.TextSecurePreferences;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {AttachmentDownloadJob.class,
                                     AvatarDownloadJob.class,
                                     RetrieveProfileAvatarJob.class,
                                     AppProtectionPreferenceFragment.class,
                                     PushDecryptJob.class,
                                     LinkPreviewRepository.class})

public class SignalCommunicationModule {

  private final Context                      context;

  private SignalServiceMessageReceiver messageReceiver;

  public SignalCommunicationModule(Context context) {
    this.context       = context;
  }

  @Provides
  synchronized SignalServiceMessageReceiver provideSignalMessageReceiver() {
    if (this.messageReceiver == null) {
      this.messageReceiver = new SignalServiceMessageReceiver();
    }

    return this.messageReceiver;
  }
}
