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

public class MultiDeviceConfigurationUpdateJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceConfigurationUpdateJob.class.getSimpleName();

  private static final String KEY_READ_RECEIPTS_ENABLED                    = "read_receipts_enabled";
  private static final String KEY_TYPING_INDICATORS_ENABLED                = "typing_indicators_enabled";
  private static final String KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED = "unidentified_delivery_indicators_enabled";

  @Inject transient SignalServiceMessageSender messageSender;

  private boolean readReceiptsEnabled;
  private boolean typingIndicatorsEnabled;
  private boolean unidentifiedDeliveryIndicatorsEnabled;

  public MultiDeviceConfigurationUpdateJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MultiDeviceConfigurationUpdateJob(Context context, boolean readReceiptsEnabled, boolean typingIndicatorsEnabled, boolean unidentifiedDeliveryIndicatorsEnabled) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("__MULTI_DEVICE_CONFIGURATION_UPDATE_JOB__")
                                .withNetworkRequirement()
                                .create());

    this.readReceiptsEnabled                   = readReceiptsEnabled;
    this.typingIndicatorsEnabled               = typingIndicatorsEnabled;
    this.unidentifiedDeliveryIndicatorsEnabled = unidentifiedDeliveryIndicatorsEnabled;
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    readReceiptsEnabled                   = data.getBoolean(KEY_READ_RECEIPTS_ENABLED);
    typingIndicatorsEnabled               = data.getBoolean(KEY_TYPING_INDICATORS_ENABLED);
    unidentifiedDeliveryIndicatorsEnabled = data.getBoolean(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED);
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putBoolean(KEY_READ_RECEIPTS_ENABLED, readReceiptsEnabled)
                      .putBoolean(KEY_TYPING_INDICATORS_ENABLED, typingIndicatorsEnabled)
                      .putBoolean(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED, unidentifiedDeliveryIndicatorsEnabled)
                      .build();
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    messageSender.sendMessage(SignalServiceSyncMessage.forConfiguration(new ConfigurationMessage(Optional.of(readReceiptsEnabled),
                                                                                                 Optional.of(unidentifiedDeliveryIndicatorsEnabled),
                                                                                                 Optional.of(typingIndicatorsEnabled))),
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
