package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.PaymentDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalServiceMessageSender.IndividualSendEvents;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class PaymentNotificationSendJob extends BaseJob {

  public static final String KEY = "PaymentNotificationSendJob";

  private static final String TAG = Log.tag(PaymentNotificationSendJob.class);

  private static final String KEY_UUID      = "uuid";
  private static final String KEY_RECIPIENT = "recipient";

  private final RecipientId recipientId;
  private final UUID        uuid;

  public static Job create(@NonNull RecipientId recipientId, @NonNull UUID uuid, @NonNull String queue) {
    if (FeatureFlags.paymentsInChatMessages()) {
      return new PaymentNotificationSendJobV2(recipientId, uuid);
    } else {
      return new PaymentNotificationSendJob(recipientId, uuid, queue);
    }
  }

  private PaymentNotificationSendJob(@NonNull RecipientId recipientId,
                             @NonNull UUID uuid,
                             @NonNull String queue)
  {
    this(new Parameters.Builder()
                       .setQueue(queue)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         recipientId,
         uuid);
  }

  private PaymentNotificationSendJob(@NonNull Parameters parameters,
                                     @NonNull RecipientId recipientId,
                                     @NonNull UUID uuid)
  {
    super(parameters);

    this.recipientId = recipientId;
    this.uuid        = uuid;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
                   .putString(KEY_RECIPIENT, recipientId.serialize())
                   .putString(KEY_UUID, uuid.toString())
                   .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    PaymentDatabase paymentDatabase = SignalDatabase.payments();
    Recipient       recipient       = Recipient.resolved(recipientId);

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipientId + " not registered!");
      return;
    }

    SignalServiceMessageSender       messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, recipient);
    Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

    PaymentDatabase.PaymentTransaction payment = paymentDatabase.getPayment(uuid);

    if (payment == null) {
      Log.w(TAG, "Could not find payment, cannot send notification " + uuid);
      return;
    }

    if (payment.getReceipt() == null) {
      Log.w(TAG, "Could not find payment receipt, cannot send notification " + uuid);
      return;
    }

    SignalServiceDataMessage dataMessage = SignalServiceDataMessage.newBuilder()
                                                                   .withPayment(new SignalServiceDataMessage.Payment(new SignalServiceDataMessage.PaymentNotification(payment.getReceipt(), payment.getNote()), null))
                                                                   .build();

    SendMessageResult sendMessageResult = messageSender.sendDataMessage(address, unidentifiedAccess, ContentHint.DEFAULT, dataMessage, IndividualSendEvents.EMPTY, false, recipient.needsPniSignature());

    if (recipient.needsPniSignature()) {
      SignalDatabase.pendingPniSignatureMessages().insertIfNecessary(recipientId, dataMessage.getTimestamp(), sendMessageResult);
    }

    if (sendMessageResult.getIdentityFailure() != null) {
      Log.w(TAG, "Identity failure for " + recipient.getId());
    } else if (sendMessageResult.isUnregisteredFailure()) {
      Log.w(TAG, "Unregistered failure for " + recipient.getId());
    } else if (sendMessageResult.getSuccess() == null) {
      throw new RetryLaterException();
    } else {
      Log.i(TAG, String.format("Payment notification sent to %s for %s", recipientId, uuid));
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    if (e instanceof NotPushRegisteredException) return false;
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, String.format("Failed to send payment notification to recipient %s for %s", recipientId, uuid));
  }

  public static class Factory implements Job.Factory<PaymentNotificationSendJob> {
    @Override
    public @NonNull PaymentNotificationSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PaymentNotificationSendJob(parameters,
                                            RecipientId.from(data.getString(KEY_RECIPIENT)),
                                            UUID.fromString(data.getString(KEY_UUID)));
    }
  }
}
