package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.thoughtcrime.securesms.jobs.AvatarDownloadJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment;

import dagger.Module;

@Module(complete = false, injects = {AvatarDownloadJob.class,
                                     RetrieveProfileAvatarJob.class,
                                     AppProtectionPreferenceFragment.class,
                                     LinkPreviewRepository.class})

public class SignalCommunicationModule {

  private final Context                      context;

  public SignalCommunicationModule(Context context) {
    this.context = context;
  }
}
