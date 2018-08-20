package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;

public class PushContentReceiveJob extends PushReceivedJob {

  private static final long   serialVersionUID = 5685475456901715638L;
  private static final String TAG              = PushContentReceiveJob.class.getSimpleName();

  private final String data;

  public PushContentReceiveJob(Context context) {
    super(context, JobParameters.newBuilder().create());
    this.data = null;
  }

  public PushContentReceiveJob(Context context, String data) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withWakeLock(true)
                                .create());

    this.data = data;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() {
    try {
      String                sessionKey = TextSecurePreferences.getSignalingKey(context);
      SignalServiceEnvelope envelope   = new SignalServiceEnvelope(data, sessionKey);

      handle(envelope);
    } catch (IOException | InvalidVersionException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }
}
