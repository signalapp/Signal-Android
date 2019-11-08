package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

public class MultiDeviceProfileContentUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceProfileContentUpdateJob";

  private static final String TAG = Log.tag(MultiDeviceProfileContentUpdateJob.class);

  public MultiDeviceProfileContentUpdateJob() {
    this(new Parameters.Builder()
                       .setQueue("MultiDeviceProfileUpdateJob")
                       .addConstraint(NetworkConstraint.KEY)
                       .setMaxAttempts(10)
                       .build());
  }

  private MultiDeviceProfileContentUpdateJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();

    messageSender.sendMessage(SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE),
                              UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }


  @Override
  public void onCanceled() {
    Log.w(TAG, "Did not succeed!");
  }

  public static final class Factory implements Job.Factory<MultiDeviceProfileContentUpdateJob> {
    @Override
    public @NonNull MultiDeviceProfileContentUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceProfileContentUpdateJob(parameters);
    }
  }
}
