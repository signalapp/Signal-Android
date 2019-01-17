package org.thoughtcrime.securesms.jobs;


import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.SafeData;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

import androidx.work.Data;
import androidx.work.WorkerParameters;

/**
 * Use {@link MultiDeviceConfigurationUpdateJob}.
 */
@Deprecated
public class MultiDeviceReadReceiptUpdateJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceReadReceiptUpdateJob.class.getSimpleName();

  private static final String KEY_ENABLED = "enabled";

  @Inject transient SignalServiceMessageSender messageSender;

  private boolean enabled;

  public MultiDeviceReadReceiptUpdateJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MultiDeviceReadReceiptUpdateJob(Context context, boolean enabled) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("__MULTI_DEVICE_READ_RECEIPT_UPDATE_JOB__")
                                .withNetworkRequirement()
                                .create());

    this.enabled = enabled;
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    enabled = data.getBoolean(KEY_ENABLED);
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putBoolean(KEY_ENABLED, enabled).build();
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    messageSender.sendMessage(SignalServiceSyncMessage.forConfiguration(new ConfigurationMessage(Optional.of(enabled), Optional.absent(), Optional.absent())),
                              UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "**** Failed to synchronize read receipts state!");
  }
}
