package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.Release;
import org.thoughtcrime.securesms.push.TextSecurePushTrustStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.push.PushAddress;
import org.whispersystems.textsecure.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.textsecure.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DeliveryReceiptJob extends ContextJob {

  private static final String TAG = DeliveryReceiptJob.class.getSimpleName();

  private final String destination;
  private final long   timestamp;
  private final String relay;

  public DeliveryReceiptJob(Context context, String destination, long timestamp, String relay) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withRetryCount(50)
                                .create());

    this.destination = destination;
    this.timestamp   = timestamp;
    this.relay       = relay;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    Log.w("DeliveryReceiptJob", "Sending delivery receipt...");
    TextSecureMessageSender messageSender =
        new TextSecureMessageSender(Release.PUSH_URL,
                                    new TextSecurePushTrustStore(context),
                                    TextSecurePreferences.getLocalNumber(context),
                                    TextSecurePreferences.getPushServerPassword(context),
                                    null, Optional.<TextSecureMessageSender.EventListener>absent());

    PushAddress pushAddress = new PushAddress(-1, destination, 1, relay);
    messageSender.sendDeliveryReceipt(pushAddress, timestamp);
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to send receipt after retry exhausted!");
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    Log.w(TAG, throwable);
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    if (throwable instanceof PushNetworkException)               return true;

    return false;
  }
}
