package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.whispersystems.signalservice.internal.EmptyResponse;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

/**
 * Job to redeem a verified donation receipt. It is up to the Job prior in the chain to specify a valid
 * presentation object via setOutputData. This is expected to be the byte[] blob of a ReceiptCredentialPresentation object.
 *
 * @deprecated Replaced with InAppPaymentRedemptionJob
 */
@Deprecated
public class DonationReceiptRedemptionJob extends BaseJob {
  private static final String TAG         = Log.tag(DonationReceiptRedemptionJob.class);
  private static final long   NO_ID       = -1L;
  private static final int    MAX_RETRIES = 1500;

  public static final String SUBSCRIPTION_QUEUE                    = "ReceiptRedemption";
  public static final String ONE_TIME_QUEUE                        = "BoostReceiptRedemption";
  public static final String KEY                                   = "DonationReceiptRedemptionJob";

  private static final String LONG_RUNNING_QUEUE_SUFFIX            = "__LONG_RUNNING";

  public static final String INPUT_RECEIPT_CREDENTIAL_PRESENTATION = "data.receipt.credential.presentation";
  public static final String INPUT_TERMINAL_DONATION               = "data.terminal.donation";
  public static final String INPUT_KEEP_ALIVE_409                  = "data.keep.alive.409";
  public static final String DATA_ERROR_SOURCE                     = "data.error.source";
  public static final String DATA_GIFT_MESSAGE_ID                  = "data.gift.message.id";
  public static final String DATA_PRIMARY                          = "data.primary";
  public static final String DATA_UI_SESSION_KEY                   = "data.ui.session.key";

  private final long                giftMessageId;
  private final boolean             makePrimary;
  private final DonationErrorSource errorSource;
  private final long                uiSessionKey;

  private       TerminalDonationQueue.TerminalDonation terminalDonation;

  public static DonationReceiptRedemptionJob createJobForSubscription(@NonNull DonationErrorSource errorSource, long uiSessionKey, boolean isLongRunningDonationPaymentType) {
    return new DonationReceiptRedemptionJob(
        NO_ID,
        false,
        errorSource,
        uiSessionKey,
        new Job.Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(SUBSCRIPTION_QUEUE + (isLongRunningDonationPaymentType ? LONG_RUNNING_QUEUE_SUFFIX : ""))
            .setMaxAttempts(MAX_RETRIES)
            .setLifespan(Parameters.IMMORTAL)
            .build());
  }

  public static DonationReceiptRedemptionJob createJobForBoost(long uiSessionKey, boolean isLongRunningDonationPaymentType) {
    return new DonationReceiptRedemptionJob(
        NO_ID,
        false,
        DonationErrorSource.ONE_TIME,
        uiSessionKey,
        new Job.Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(ONE_TIME_QUEUE + (isLongRunningDonationPaymentType ? LONG_RUNNING_QUEUE_SUFFIX : ""))
            .setMaxAttempts(MAX_RETRIES)
            .setLifespan(Parameters.IMMORTAL)
            .build());
  }

  public static JobManager.Chain createJobChainForKeepAlive() {
    DonationReceiptRedemptionJob       redemptionJob                      = createJobForSubscription(DonationErrorSource.KEEP_ALIVE, -1L, false);
    RefreshOwnProfileJob               refreshOwnProfileJob               = new RefreshOwnProfileJob();
    MultiDeviceProfileContentUpdateJob multiDeviceProfileContentUpdateJob = new MultiDeviceProfileContentUpdateJob();

    return AppDependencies.getJobManager()
                          .startChain(redemptionJob)
                          .then(refreshOwnProfileJob)
                          .then(multiDeviceProfileContentUpdateJob);
  }

  private DonationReceiptRedemptionJob(long giftMessageId, boolean primary, @NonNull DonationErrorSource errorSource, long uiSessionKey, @NonNull Job.Parameters parameters) {
    super(parameters);
    this.giftMessageId                    = giftMessageId;
    this.makePrimary                      = primary;
    this.errorSource                      = errorSource;
    this.uiSessionKey                     = uiSessionKey;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder()
                   .putString(DATA_ERROR_SOURCE, errorSource.serialize())
                   .putLong(DATA_GIFT_MESSAGE_ID, giftMessageId)
                   .putBoolean(DATA_PRIMARY, makePrimary)
                   .putLong(DATA_UI_SESSION_KEY, uiSessionKey)
                   .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
    if (getInputData() == null) {
      Log.d(TAG, "No input data, assuming upstream job in chain failed and properly set error state. Failing without side effects.");
      return;
    }

    if (isForSubscription()) {
      Log.d(TAG, "Marking subscription failure", true);
      SignalStore.inAppPayments().markSubscriptionRedemptionFailed();
      MultiDeviceSubscriptionSyncRequestJob.enqueue();
    } else if (giftMessageId != NO_ID) {
      SignalDatabase.messages().markGiftRedemptionFailed(giftMessageId);
    }

    if (terminalDonation != null) {
      SignalStore.inAppPayments().appendToTerminalDonationQueue(terminalDonation);
    }
  }

  @Override
  public void onAdded() {
    if (giftMessageId != NO_ID) {
      SignalDatabase.messages().markGiftRedemptionStarted(giftMessageId);
    }
  }

  @Override
  protected void onRun() throws Exception {
    if (isForSubscription()) {
      synchronized (InAppPaymentSubscriberRecord.Type.DONATION) {
        doRun();
      }
    } else {
      doRun();
    }
  }

  private void doRun() throws Exception {
    JsonJobData inputData      = getInputData() != null ? JsonJobData.deserialize(getInputData()) : null;
    boolean     isKeepAlive409 = inputData != null && inputData.getBooleanOrDefault(INPUT_KEEP_ALIVE_409, false);

    if (isKeepAlive409) {
      Log.d(TAG, "Keep-Alive redemption job hit a 409. Exiting.", true);
      return;
    }

    ReceiptCredentialPresentation presentation = getPresentation();
    if (presentation == null) {
      Log.d(TAG, "No presentation available. Exiting.", true);
      return;
    }

    byte[] rawTerminalDonation = inputData != null ? inputData.getStringAsBlob(INPUT_TERMINAL_DONATION) : null;
    if (rawTerminalDonation != null) {
      Log.d(TAG, "Retrieved terminal donation information from input data.");
      terminalDonation = TerminalDonationQueue.TerminalDonation.ADAPTER.decode(rawTerminalDonation);
    } else {
      Log.d(TAG, "Input data does not contain terminal donation data. Creating one with sane defaults.");
      terminalDonation = new TerminalDonationQueue.TerminalDonation.Builder()
          .level(presentation.getReceiptLevel())
          .build();
    }

    Log.d(TAG, "Attempting to redeem token... isForSubscription: " + isForSubscription(), true);
    ServiceResponse<EmptyResponse> response = AppDependencies.getDonationsService()
                                                             .redeemDonationReceipt(presentation,
                                                                                    SignalStore.inAppPayments().getDisplayBadgesOnProfile(),
                                                                                    makePrimary);

    if (response.getApplicationError().isPresent()) {
      if (response.getStatus() >= 500) {
        Log.w(TAG, "Encountered a server exception " + response.getStatus(), response.getApplicationError().get(), true);
        throw new RetryableException();
      } else {
        Log.w(TAG, "Encountered a non-recoverable exception " + response.getStatus(), response.getApplicationError().get(), true);
        DonationError.routeBackgroundError(context, DonationError.genericBadgeRedemptionFailure(errorSource));

        if (isForOneTimeDonation()) {
          DonationErrorValue donationErrorValue = new DonationErrorValue.Builder()
              .type(DonationErrorValue.Type.REDEMPTION)
              .code(Integer.toString(response.getStatus()))
              .build();

          SignalStore.inAppPayments().setPendingOneTimeDonationError(
              donationErrorValue
          );

          terminalDonation = terminalDonation.newBuilder()
                                             .error(donationErrorValue)
                                             .build();
        }

        throw new IOException(response.getApplicationError().get());
      }
    } else if (response.getExecutionError().isPresent()) {
      Log.w(TAG, "Encountered a retryable exception", response.getExecutionError().get(), true);
      throw new RetryableException();
    }

    Log.i(TAG, "Successfully redeemed token with response code " + response.getStatus() + "... isForSubscription: " + isForSubscription(), true);
    enqueueDonationComplete();

    if (isForSubscription()) {
      Log.d(TAG, "Clearing subscription failure", true);
      SignalStore.inAppPayments().clearSubscriptionRedemptionFailed();
      Log.i(TAG, "Recording end of period from active subscription", true);
      SignalStore.inAppPayments()
                 .setSubscriptionEndOfPeriodRedeemed(SignalStore.inAppPayments()
                                                                .getSubscriptionEndOfPeriodRedemptionStarted());
      SignalStore.inAppPayments().clearSubscriptionReceiptCredential();
    } else if (giftMessageId != NO_ID) {
      Log.d(TAG, "Marking gift redemption completed for " + giftMessageId);
      SignalDatabase.messages().markGiftRedemptionCompleted(giftMessageId);
      MessageTable.MarkedMessageInfo markedMessageInfo = SignalDatabase.messages().setIncomingMessageViewed(giftMessageId);
      if (markedMessageInfo != null) {
        Log.d(TAG, "Marked gift message viewed for " + giftMessageId);
        MultiDeviceViewedUpdateJob.enqueue(Collections.singletonList(markedMessageInfo.getSyncMessageId()));
      }
    }

    if (isForOneTimeDonation()) {
      SignalStore.inAppPayments().setPendingOneTimeDonation(null);
    }
  }

  private @Nullable ReceiptCredentialPresentation getPresentation() throws InvalidInputException, NoSuchMessageException {
    final ReceiptCredentialPresentation receiptCredentialPresentation;

    if (isForSubscription()) {
      receiptCredentialPresentation = SignalStore.inAppPayments().getSubscriptionReceiptCredential();
    } else {
      receiptCredentialPresentation = null;
    }

    if (receiptCredentialPresentation != null) {
      return receiptCredentialPresentation;
    } if (giftMessageId == NO_ID) {
      return getPresentationFromInputData();
    } else {
      return getPresentationFromGiftMessage();
    }
  }

  private @Nullable ReceiptCredentialPresentation getPresentationFromInputData() throws InvalidInputException {
    JsonJobData inputData = JsonJobData.deserialize(getInputData());

    if (inputData.isEmpty()) {
      Log.w(TAG, "No input data. Exiting.", true);
      return null;
    }

    byte[] presentationBytes = inputData.getStringAsBlob(INPUT_RECEIPT_CREDENTIAL_PRESENTATION);
    if (presentationBytes == null) {
      Log.d(TAG, "No response data. Exiting.", true);
      return null;
    }

    return new ReceiptCredentialPresentation(presentationBytes);
  }

  private @Nullable ReceiptCredentialPresentation getPresentationFromGiftMessage() throws InvalidInputException, NoSuchMessageException {
    MessageRecord messageRecord = SignalDatabase.messages().getMessageRecord(giftMessageId);

    if (MessageRecordUtil.hasGiftBadge(messageRecord)) {
      GiftBadge giftBadge = MessageRecordUtil.requireGiftBadge(messageRecord);
      if (giftBadge.redemptionState == GiftBadge.RedemptionState.REDEEMED) {
        Log.d(TAG, "Already redeemed this gift badge. Exiting.", true);
        return null;
      } else {
        Log.d(TAG, "Attempting redemption  of badge in state " + giftBadge.redemptionState.name());
        return new ReceiptCredentialPresentation(giftBadge.redemptionToken.toByteArray());
      }
    } else {
      Log.d(TAG, "No gift badge on message record. Exiting.", true);
      return null;
    }
  }

  private boolean isForSubscription() {
    return Objects.requireNonNull(getParameters().getQueue()).startsWith(SUBSCRIPTION_QUEUE);
  }

  private boolean isForOneTimeDonation() {
    return Objects.requireNonNull(getParameters().getQueue()).startsWith(ONE_TIME_QUEUE) && giftMessageId == NO_ID;
  }

  private void enqueueDonationComplete() {
    if (errorSource == DonationErrorSource.GIFT || errorSource == DonationErrorSource.GIFT_REDEMPTION) {
      Log.i(TAG, "Skipping donation complete sheet for GIFT related redemption.");
      return;
    }

    if (errorSource == DonationErrorSource.KEEP_ALIVE) {
      Log.i(TAG, "Skipping donation complete sheet for subscription KEEP_ALIVE jobchain.");
      return;
    }

    SignalStore.inAppPayments().appendToTerminalDonationQueue(terminalDonation);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryableException;
  }

  private final static class RetryableException extends Exception {
  }

  public static class Factory implements Job.Factory<DonationReceiptRedemptionJob> {
    @Override
    public @NonNull DonationReceiptRedemptionJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      String              serializedErrorSource            = data.getStringOrDefault(DATA_ERROR_SOURCE, DonationErrorSource.UNKNOWN.serialize());
      long                messageId                        = data.getLongOrDefault(DATA_GIFT_MESSAGE_ID, NO_ID);
      boolean             primary                          = data.getBooleanOrDefault(DATA_PRIMARY, false);
      DonationErrorSource errorSource                      = DonationErrorSource.deserialize(serializedErrorSource);
      long                uiSessionKey                     = data.getLongOrDefault(DATA_UI_SESSION_KEY, -1L);

      return new DonationReceiptRedemptionJob(messageId, primary, errorSource, uiSessionKey, parameters);
    }
  }
}
