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
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.whispersystems.signalservice.internal.EmptyResponse;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Job to redeem a verified donation receipt. It is up to the Job prior in the chain to specify a valid
 * presentation object via setOutputData. This is expected to be the byte[] blob of a ReceiptCredentialPresentation object.
 */
public class DonationReceiptRedemptionJob extends BaseJob {
  private static final String TAG   = Log.tag(DonationReceiptRedemptionJob.class);
  private static final long   NO_ID = -1L;

  public static final String SUBSCRIPTION_QUEUE                    = "ReceiptRedemption";
  public static final String KEY                                   = "DonationReceiptRedemptionJob";
  public static final String INPUT_RECEIPT_CREDENTIAL_PRESENTATION = "data.receipt.credential.presentation";
  public static final String INPUT_KEEP_ALIVE_409                  = "data.keep.alive.409";
  public static final String DATA_ERROR_SOURCE                     = "data.error.source";
  public static final String DATA_GIFT_MESSAGE_ID                  = "data.gift.message.id";
  public static final String DATA_PRIMARY                          = "data.primary";

  private final long                giftMessageId;
  private final boolean             makePrimary;
  private final DonationErrorSource errorSource;

  public static DonationReceiptRedemptionJob createJobForSubscription(@NonNull DonationErrorSource errorSource) {
    return new DonationReceiptRedemptionJob(
        NO_ID,
        false,
        errorSource,
        new Job.Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(SUBSCRIPTION_QUEUE)
            .setMaxAttempts(Parameters.UNLIMITED)
            .setMaxInstancesForQueue(1)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .build());
  }

  public static DonationReceiptRedemptionJob createJobForBoost() {
    return new DonationReceiptRedemptionJob(
        NO_ID,
        false,
        DonationErrorSource.BOOST,
        new Job.Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("BoostReceiptRedemption")
            .setMaxAttempts(Parameters.UNLIMITED)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .build());
  }

  public static JobManager.Chain createJobChainForKeepAlive() {
    DonationReceiptRedemptionJob       redemptionJob                      = createJobForSubscription(DonationErrorSource.KEEP_ALIVE);
    RefreshOwnProfileJob               refreshOwnProfileJob               = new RefreshOwnProfileJob();
    MultiDeviceProfileContentUpdateJob multiDeviceProfileContentUpdateJob = new MultiDeviceProfileContentUpdateJob();

    return ApplicationDependencies.getJobManager()
                                  .startChain(redemptionJob)
                                  .then(refreshOwnProfileJob)
                                  .then(multiDeviceProfileContentUpdateJob);
  }

  public static JobManager.Chain createJobChainForGift(long messageId, boolean primary) {
    DonationReceiptRedemptionJob redeemReceiptJob = new DonationReceiptRedemptionJob(
        messageId,
        primary,
        DonationErrorSource.GIFT_REDEMPTION,
        new Job.Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("GiftReceiptRedemption-" + messageId)
            .setMaxAttempts(Parameters.UNLIMITED)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .build());

    RefreshOwnProfileJob               refreshOwnProfileJob               = new RefreshOwnProfileJob();
    MultiDeviceProfileContentUpdateJob multiDeviceProfileContentUpdateJob = new MultiDeviceProfileContentUpdateJob();

    return ApplicationDependencies.getJobManager()
                                  .startChain(redeemReceiptJob)
                                  .then(refreshOwnProfileJob)
                                  .then(multiDeviceProfileContentUpdateJob);
  }

  private DonationReceiptRedemptionJob(long giftMessageId, boolean primary, @NonNull DonationErrorSource errorSource, @NonNull Job.Parameters parameters) {
    super(parameters);
    this.giftMessageId = giftMessageId;
    this.makePrimary   = primary;
    this.errorSource   = errorSource;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder()
                   .putString(DATA_ERROR_SOURCE, errorSource.serialize())
                   .putLong(DATA_GIFT_MESSAGE_ID, giftMessageId)
                   .putBoolean(DATA_PRIMARY, makePrimary)
                   .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
    if (isForSubscription()) {
      Log.d(TAG, "Marking subscription failure", true);
      SignalStore.donationsValues().markSubscriptionRedemptionFailed();
      MultiDeviceSubscriptionSyncRequestJob.enqueue();
    } else if (giftMessageId != NO_ID) {
      SignalDatabase.messages().markGiftRedemptionFailed(giftMessageId);
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
      synchronized (SubscriptionReceiptRequestResponseJob.MUTEX) {
        doRun();
      }
    } else {
      doRun();
    }
  }

  private void doRun() throws Exception {
    boolean isKeepAlive409 = getInputData() != null && JsonJobData.deserialize(getInputData()).getBooleanOrDefault(INPUT_KEEP_ALIVE_409, false);
    if (isKeepAlive409) {
      Log.d(TAG, "Keep-Alive redemption job hit a 409. Exiting.", true);
      return;
    }

    ReceiptCredentialPresentation presentation = getPresentation();
    if (presentation == null) {
      Log.d(TAG, "No presentation available. Exiting.", true);
      return;
    }

    Log.d(TAG, "Attempting to redeem token... isForSubscription: " + isForSubscription(), true);
    ServiceResponse<EmptyResponse> response = ApplicationDependencies.getDonationsService()
                                                                     .redeemReceipt(presentation,
                                                                                    SignalStore.donationsValues().getDisplayBadgesOnProfile(),
                                                                                    makePrimary);

    if (response.getApplicationError().isPresent()) {
      if (response.getStatus() >= 500) {
        Log.w(TAG, "Encountered a server exception " + response.getStatus(), response.getApplicationError().get(), true);
        throw new RetryableException();
      } else {
        Log.w(TAG, "Encountered a non-recoverable exception " + response.getStatus(), response.getApplicationError().get(), true);
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(errorSource));
        throw new IOException(response.getApplicationError().get());
      }
    } else if (response.getExecutionError().isPresent()) {
      Log.w(TAG, "Encountered a retryable exception", response.getExecutionError().get(), true);
      throw new RetryableException();
    }

    Log.i(TAG, "Successfully redeemed token with response code " + response.getStatus() + "... isForSubscription: " + isForSubscription(), true);

    if (isForSubscription()) {
      Log.d(TAG, "Clearing subscription failure", true);
      SignalStore.donationsValues().clearSubscriptionRedemptionFailed();
      Log.i(TAG, "Recording end of period from active subscription", true);
      SignalStore.donationsValues()
                 .setSubscriptionEndOfPeriodRedeemed(SignalStore.donationsValues()
                                                                .getSubscriptionEndOfPeriodRedemptionStarted());
      SignalStore.donationsValues().clearSubscriptionReceiptCredential();
    } else if (giftMessageId != NO_ID) {
      Log.d(TAG, "Marking gift redemption completed for " + giftMessageId);
      SignalDatabase.messages().markGiftRedemptionCompleted(giftMessageId);
      MessageTable.MarkedMessageInfo markedMessageInfo = SignalDatabase.messages().setIncomingMessageViewed(giftMessageId);
      if (markedMessageInfo != null) {
        Log.d(TAG, "Marked gift message viewed for " + giftMessageId);
        MultiDeviceViewedUpdateJob.enqueue(Collections.singletonList(markedMessageInfo.getSyncMessageId()));
      }
    }
  }

  private @Nullable ReceiptCredentialPresentation getPresentation() throws InvalidInputException, NoSuchMessageException {
    final ReceiptCredentialPresentation receiptCredentialPresentation;

    if (isForSubscription()) {
      receiptCredentialPresentation = SignalStore.donationsValues().getSubscriptionReceiptCredential();
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
      if (giftBadge.getRedemptionState() == GiftBadge.RedemptionState.REDEEMED) {
        Log.d(TAG, "Already redeemed this gift badge. Exiting.", true);
        return null;
      } else {
        Log.d(TAG, "Attempting redemption  of badge in state " + giftBadge.getRedemptionState().name());
        return new ReceiptCredentialPresentation(giftBadge.getRedemptionToken().toByteArray());
      }
    } else {
      Log.d(TAG, "No gift badge on message record. Exiting.", true);
      return null;
    }
  }

  private boolean isForSubscription() {
    return Objects.equals(getParameters().getQueue(), SUBSCRIPTION_QUEUE);
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

      String              serializedErrorSource = data.getStringOrDefault(DATA_ERROR_SOURCE, DonationErrorSource.UNKNOWN.serialize());
      long                messageId             = data.getLongOrDefault(DATA_GIFT_MESSAGE_ID, NO_ID);
      boolean             primary               = data.getBooleanOrDefault(DATA_PRIMARY, false);
      DonationErrorSource errorSource           = DonationErrorSource.deserialize(serializedErrorSource);

      return new DonationReceiptRedemptionJob(messageId, primary, errorSource, parameters);
    }
  }
}
