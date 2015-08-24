package org.thoughtcrime.securesms.jobs.requirements;

import android.content.Context;
import android.util.Log;

import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.jobqueue.requirements.RequirementProvider;

import de.greenrobot.event.EventBus;

public class MediaNetworkRequirementProvider implements RequirementProvider {

  private RequirementListener listener;

  public MediaNetworkRequirementProvider(Context context) {
    EventBus.getDefault().register(this);
  }

  @SuppressWarnings("unused")
  public void onEvent(MediaDownloadControlEvent event) {
    if (listener != null) {
      listener.onRequirementStatusChanged();
    }
  }

  @Override
  public void setListener(RequirementListener listener) {
    this.listener = listener;
  }

  public static class MediaDownloadControlEvent {}
}
