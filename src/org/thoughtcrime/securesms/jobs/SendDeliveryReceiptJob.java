package org.thoughtcrime.securesms.jobs;


import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.SafeData;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.Collections;

import javax.inject.Inject;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class SendDeliveryReceiptJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String KEY_ADDRESS    = "address";
  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_TIMESTAMP  = "timestamp";

  private static final String TAG = SendReadReceiptJob.class.getSimpleName();

  @Inject
  transient SignalServiceMessageSender messageSender;

  private String address;
  private long   messageId;
  private long   timestamp;

  public SendDeliveryReceiptJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public SendDeliveryReceiptJob(Context context, Address address, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .create());

    this.address   = address.serialize();
    this.messageId = messageId;
    this.timestamp = System.currentTimeMillis();
  }

  @Override
  public void onAdded() {}

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putString(KEY_ADDRESS, address)
                      .putLong(KEY_MESSAGE_ID, messageId)
                      .putLong(KEY_TIMESTAMP, timestamp)
                      .build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    this.address   = data.getString(KEY_ADDRESS);
    this.messageId = data.getLong(KEY_MESSAGE_ID);
    this.timestamp = data.getLong(KEY_TIMESTAMP);
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    SignalServiceAddress        remoteAddress  = new SignalServiceAddress(address);
    SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY,
                                                                                 Collections.singletonList(messageId),
                                                                                 timestamp);

    messageSender.sendReceipt(remoteAddress,
                              UnidentifiedAccessUtil.getAccessFor(context, Recipient.from(context, Address.fromSerialized(address), false)),
                              receiptMessage);
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    if (e instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to send delivery receipt to: " + address);
  }

}
