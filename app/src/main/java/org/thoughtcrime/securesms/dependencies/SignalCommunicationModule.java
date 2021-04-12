package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.session.libsignal.service.api.SignalServiceMessageReceiver;
import org.thoughtcrime.securesms.jobs.AvatarDownloadJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {AvatarDownloadJob.class,
                                     RetrieveProfileAvatarJob.class,
                                     AppProtectionPreferenceFragment.class,
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
