package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.PaymentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.messages.multidevice.OutgoingPaymentMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tells a linked device about sent payments.
 */
public final class MultiDeviceOutgoingPaymentSyncJob extends BaseJob {

  private static final String TAG = Log.tag(MultiDeviceOutgoingPaymentSyncJob.class);

  public static final String KEY = "MultiDeviceOutgoingPaymentSyncJob";

  private static final String KEY_UUID = "uuid";

  private final UUID uuid;

  public MultiDeviceOutgoingPaymentSyncJob(@NonNull UUID sentPaymentId) {
    this(new Parameters.Builder()
                       .setQueue("MultiDeviceOutgoingPaymentSyncJob")
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .build(),
         sentPaymentId);
  }

  private MultiDeviceOutgoingPaymentSyncJob(@NonNull Parameters parameters,
                                            @NonNull UUID sentPaymentId)
  {
    super(parameters);
    this.uuid = sentPaymentId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder()
                   .putString(KEY_UUID, uuid.toString())
                   .serialize();
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

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    PaymentTable.PaymentTransaction payment = SignalDatabase.payments().getPayment(uuid);

    if (payment == null) {
      Log.w(TAG, "Payment not found " + uuid);
      return;
    }

    PaymentMetaData.MobileCoinTxoIdentification txoIdentification = payment.getPaymentMetaData().getMobileCoinTxoIdentification();

    boolean defrag = payment.isDefrag();

    Optional<ServiceId> uuid;
    if (!defrag && payment.getPayee().hasRecipientId()) {
      uuid = Optional.of(RecipientUtil.getOrFetchServiceId(context, Recipient.resolved(payment.getPayee().requireRecipientId())));
    } else {
      uuid = Optional.empty();
    }

    byte[] receipt = payment.getReceipt();

    if (receipt == null) {
      throw new AssertionError("Trying to sync payment before sent?");
    }

    OutgoingPaymentMessage outgoingPaymentMessage = new OutgoingPaymentMessage(uuid,
                                                                               payment.getAmount().requireMobileCoin(),
                                                                               payment.getFee().requireMobileCoin(),
                                                                               ByteString.copyFrom(receipt),
                                                                               payment.getBlockIndex(),
                                                                               payment.getTimestamp(),
                                                                               defrag ? Optional.empty() : Optional.of(payment.getPayee().requirePublicAddress().serialize()),
                                                                               defrag ? Optional.empty() : Optional.of(payment.getNote()),
                                                                               txoIdentification.getPublicKeyList(),
                                                                               txoIdentification.getKeyImagesList());


    ApplicationDependencies.getSignalServiceMessageSender()
                           .sendSyncMessage(SignalServiceSyncMessage.forOutgoingPayment(outgoingPaymentMessage),
                                            UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to sync sent payment!");
  }

  public static class Factory implements Job.Factory<MultiDeviceOutgoingPaymentSyncJob> {

    @Override
    public @NonNull MultiDeviceOutgoingPaymentSyncJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new MultiDeviceOutgoingPaymentSyncJob(parameters,
                                                   UUID.fromString(data.getString(KEY_UUID)));
    }
  }
}
