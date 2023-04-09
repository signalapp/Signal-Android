package org.thoughtcrime.securesms.jobs;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.PaymentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.payments.FailureReason;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.payments.PaymentSubmissionResult;
import org.thoughtcrime.securesms.payments.PaymentTransactionId;
import org.thoughtcrime.securesms.payments.TransactionSubmissionResult;
import org.thoughtcrime.securesms.payments.Wallet;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.Stopwatch;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Allows payment to a recipient or a public address.
 */
public final class PaymentSendJob extends BaseJob {

  private static final String TAG = Log.tag(PaymentSendJob.class);

  public static final String KEY = "PaymentSendJob";

  static final String QUEUE = "Payments";

  private static final String KEY_UUID      = "uuid";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_RECIPIENT = "recipient";
  private static final String KEY_ADDRESS   = "public_address";
  private static final String KEY_NOTE      = "note";
  private static final String KEY_AMOUNT    = "amount";
  private static final String KEY_FEE       = "fee";

  private final UUID                    uuid;
  private final long                    timestamp;
  private final RecipientId             recipientId;
  private final MobileCoinPublicAddress publicAddress;
  private final String                  note;
  private final Money                   amount;
  private final Money                   totalFee;

  /**
   * @param totalFee Total quoted totalFee to the user. This is expected to cover all defrags and the actual transaction.
   */
  @AnyThread
  public static @NonNull UUID enqueuePayment(@Nullable RecipientId recipientId,
                                             @NonNull MobileCoinPublicAddress publicAddress,
                                             @NonNull String note,
                                             @NonNull Money amount,
                                             @NonNull Money totalFee)
  {
    UUID uuid      = UUID.randomUUID();
    long timestamp = System.currentTimeMillis();

    Job sendJob = new PaymentSendJob(new Parameters.Builder()
                                                   .setQueue(QUEUE)
                                                   .setMaxAttempts(1)
                                                   .build(),
                                     uuid,
                                     timestamp,
                                     recipientId,
                                     Objects.requireNonNull(publicAddress),
                                     note,
                                     amount,
                                     totalFee);

    JobManager.Chain chain = ApplicationDependencies.getJobManager()
                                                    .startChain(sendJob)
                                                    .then(new PaymentTransactionCheckJob(uuid, QUEUE))
                                                    .then(new MultiDeviceOutgoingPaymentSyncJob(uuid));

    if (recipientId != null) {
      chain.then(PaymentNotificationSendJob.create(recipientId, uuid, recipientId.toQueueKey(true)));
    }

    chain.then(PaymentLedgerUpdateJob.updateLedgerToReflectPayment(uuid))
         .enqueue();

    return uuid;
  }

  private PaymentSendJob(@NonNull Parameters parameters,
                         @NonNull UUID uuid,
                         long timestamp,
                         @Nullable RecipientId recipientId,
                         @NonNull MobileCoinPublicAddress publicAddress,
                         @NonNull String note,
                         @NonNull Money amount,
                         @NonNull Money totalFee)
  {
    super(parameters);
    this.uuid          = uuid;
    this.timestamp     = timestamp;
    this.recipientId   = recipientId;
    this.publicAddress = publicAddress;
    this.note          = note;
    this.amount        = amount;
    this.totalFee      = totalFee;
  }

  /**
   * Use to track the payment in the database.
   * <p>
   * Present in the database only after it has been submitted successfully.
   */
  public @NonNull UUID getUuid() {
    return uuid;
  }

  @Override
  protected void onRun() throws Exception {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!SignalStore.paymentsValues().mobileCoinPaymentsEnabled()) {
      Log.w(TAG, "Payments are not enabled");
      return;
    }

    Stopwatch stopwatch = new Stopwatch("Payment submission");

    Wallet       wallet          = ApplicationDependencies.getPayments().getWallet();
    PaymentTable paymentDatabase = SignalDatabase.payments();

    paymentDatabase.createOutgoingPayment(uuid,
                                          recipientId,
                                          publicAddress,
                                          timestamp,
                                          note,
                                          amount);
    Log.i(TAG, "Payment record created " + uuid);
    stopwatch.split("Record created");

    try {
      PaymentSubmissionResult paymentSubmissionResult = wallet.sendPayment(publicAddress, amount.requireMobileCoin(), totalFee.requireMobileCoin());
      stopwatch.split("Payment submitted");

      if (paymentSubmissionResult.containsDefrags()) {
        Log.i(TAG, "Payment contains " + paymentSubmissionResult.defrags().size() + " defrags, main payment" + uuid);
        RecipientId             self        = Recipient.self().getId();
        MobileCoinPublicAddress selfAddress = wallet.getMobileCoinPublicAddress();
        for (TransactionSubmissionResult defrag : paymentSubmissionResult.defrags()) {
          UUID                            defragUuid            = UUID.randomUUID();
          PaymentTransactionId.MobileCoin mobileCoinTransaction = (PaymentTransactionId.MobileCoin) defrag.getTransactionId();
          paymentDatabase.createDefrag(defragUuid,
                                       self,
                                       selfAddress,
                                       timestamp - 1,
                                       mobileCoinTransaction.getFee(),
                                       mobileCoinTransaction.getTransaction(), mobileCoinTransaction.getReceipt());
          Log.i(TAG, "Defrag entered with id " + defragUuid);
          ApplicationDependencies.getJobManager()
                                 .startChain(new PaymentTransactionCheckJob(defragUuid, QUEUE))
                                 .then(new MultiDeviceOutgoingPaymentSyncJob(defragUuid))
                                 .enqueue();
        }
        stopwatch.split("Defrag");
      }

      TransactionSubmissionResult.ErrorCode errorCode = paymentSubmissionResult.getErrorCode();

      switch (errorCode) {
        case INSUFFICIENT_FUNDS:
          paymentDatabase.markPaymentFailed(uuid, FailureReason.INSUFFICIENT_FUNDS);
          throw new PaymentException("Payment failed due to " + errorCode);
        case GENERIC_FAILURE:
          paymentDatabase.markPaymentFailed(uuid, FailureReason.UNKNOWN);
          throw new PaymentException("Payment failed due to " + errorCode);
        case NETWORK_FAILURE:
          paymentDatabase.markPaymentFailed(uuid, FailureReason.NETWORK);
          throw new PaymentException("Payment failed due to " + errorCode);
        case NONE:
          Log.i(TAG, "Payment submission complete");
          TransactionSubmissionResult transactionSubmissionResult = Objects.requireNonNull(paymentSubmissionResult.getNonDefrag());
          PaymentTransactionId.MobileCoin mobileCoinTransaction = (PaymentTransactionId.MobileCoin) transactionSubmissionResult.getTransactionId();
          paymentDatabase.markPaymentSubmitted(uuid,
                                               mobileCoinTransaction.getTransaction(),
                                               mobileCoinTransaction.getReceipt(),
                                               mobileCoinTransaction.getFee());
          Log.i(TAG, "Payment record updated " + uuid);
          break;
      }
    } catch (Exception e) {
      Log.w(TAG, "Unknown payment failure", e);
      paymentDatabase.markPaymentFailed(uuid, FailureReason.UNKNOWN);
      throw e;
    }
    stopwatch.split("Update database record");

    stopwatch.stop(TAG);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder()
                   .putString(KEY_UUID, uuid.toString())
                   .putLong(KEY_TIMESTAMP, timestamp)
                   .putString(KEY_RECIPIENT, recipientId != null ? recipientId.serialize() : null)
                   .putString(KEY_ADDRESS, publicAddress != null ? publicAddress.getPaymentAddressBase58() : null)
                   .putString(KEY_NOTE, note)
                   .putString(KEY_AMOUNT, amount.serialize())
                   .putString(KEY_FEE, totalFee.serialize())
                   .serialize();
  }

  @NonNull @Override
  public String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to make payment to " + recipientId);
  }

  public static final class PaymentException extends Exception {
    PaymentException(@NonNull String message) {
      super(message);
    }
  }

  public static final class Factory implements Job.Factory<PaymentSendJob> {
    @Override
    public @NonNull PaymentSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new PaymentSendJob(parameters,
                                UUID.fromString(data.getString(KEY_UUID)),
                                data.getLong(KEY_TIMESTAMP),
                                RecipientId.fromNullable(data.getString(KEY_RECIPIENT)),
                                MobileCoinPublicAddress.fromBase58NullableOrThrow(data.getString(KEY_ADDRESS)),
                                data.getString(KEY_NOTE),
                                Money.parseOrThrow(data.getString(KEY_AMOUNT)),
                                Money.parseOrThrow(data.getString(KEY_FEE)));
    }
  }
}
