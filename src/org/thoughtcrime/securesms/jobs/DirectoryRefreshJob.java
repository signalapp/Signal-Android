package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DirectoryRefreshJob extends ContextJob {

  @Nullable private transient Recipient    recipient;
  @Nullable private transient MasterSecret masterSecret;
            private transient boolean      notifyOfNewUsers;

  public DirectoryRefreshJob(@NonNull Context context, boolean notifyOfNewUsers) {
    this(context, null, null, notifyOfNewUsers);
  }

  public DirectoryRefreshJob(@NonNull Context context,
                             @Nullable MasterSecret masterSecret,
                             @Nullable Recipient recipient,
                                       boolean notifyOfNewUsers)
  {
    super(context, JobParameters.newBuilder()
                                .withGroupId(DirectoryRefreshJob.class.getSimpleName())
                                .withRequirement(new NetworkRequirement(context))
                                .create());

    this.recipient        = recipient;
    this.masterSecret     = masterSecret;
    this.notifyOfNewUsers = notifyOfNewUsers;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    Log.w("DirectoryRefreshJob", "DirectoryRefreshJob.onRun()");
    PowerManager          powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock     = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Directory Refresh");

    try {
      wakeLock.acquire();
      if (recipient == null) {
        DirectoryHelper.refreshDirectory(context, KeyCachingService.getMasterSecret(context), notifyOfNewUsers);
      } else {
        DirectoryHelper.refreshDirectoryFor(context, masterSecret, recipient);
      }
      SecurityEvent.broadcastSecurityUpdateEvent(context);
    } finally {
      if (wakeLock.isHeld()) wakeLock.release();
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {}
}
