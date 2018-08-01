package org.thoughtcrime.securesms.jobs;


import android.content.Context;

import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

public class MultiDeviceReadReceiptUpdateJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceReadReceiptUpdateJob.class.getSimpleName();

  @Inject transient SignalServiceMessageSender messageSender;

  private final boolean enabled;

  public MultiDeviceReadReceiptUpdateJob(Context context, boolean enabled) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId("__MULTI_DEVICE_READ_RECEIPT_UPDATE_JOB__")
                                .withRequirement(new NetworkRequirement(context))
                                .create());

    this.enabled = enabled;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    messageSender.sendMessage(SignalServiceSyncMessage.forConfiguration(new ConfigurationMessage(Optional.of(enabled))));
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
