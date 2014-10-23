package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.service.PushDownloader;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.whispersystems.jobqueue.EncryptionKeys;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.IOException;

public class PushDownloadJob extends ContextJob {
  private static final String TAG = PushDownloadJob.class.getSimpleName();

  private long messageId;

  public PushDownloadJob(Context context, MasterSecret masterSecret, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withRetryCount(20)
                                .withPersistence()
                                .withEncryption(new EncryptionKeys(ParcelUtil.serialize(masterSecret)))
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws Exception {
    MasterSecret masterSecret = ParcelUtil.deserialize(getEncryptionKeys().getEncoded(), MasterSecret.CREATOR);

    PushDownloader pushDownloader = new PushDownloader(this.context);
    pushDownloader.process(masterSecret, this.messageId);
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "PushDownloadJob canceled");
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    Log.w(TAG, throwable.getMessage());

    if (throwable instanceof IOException) {
      return true;
    }

    return false;
  }
}
