package org.privatechats.securesms.jobs;

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.crypto.SecurityEvent;
import org.privatechats.securesms.recipients.Recipients;
import org.privatechats.securesms.service.KeyCachingService;
import org.privatechats.securesms.util.DirectoryHelper;
import org.privatechats.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.textsecure.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DirectoryRefreshJob extends ContextJob {

  @Nullable private transient Recipients   recipients;
  @Nullable private transient MasterSecret masterSecret;

  public DirectoryRefreshJob(@NonNull Context context) {
    this(context, null, null);
  }

  public DirectoryRefreshJob(@NonNull Context context,
                             @Nullable MasterSecret masterSecret,
                             @Nullable Recipients recipients)
  {
    super(context, JobParameters.newBuilder()
                                .withGroupId(DirectoryRefreshJob.class.getSimpleName())
                                .withRequirement(new NetworkRequirement(context))
                                .create());

    this.recipients   = recipients;
    this.masterSecret = masterSecret;
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
      if (recipients == null) {
        DirectoryHelper.refreshDirectory(context, KeyCachingService.getMasterSecret(context));
      } else {
        DirectoryHelper.refreshDirectoryFor(context, masterSecret, recipients, TextSecurePreferences.getLocalNumber(context));
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
