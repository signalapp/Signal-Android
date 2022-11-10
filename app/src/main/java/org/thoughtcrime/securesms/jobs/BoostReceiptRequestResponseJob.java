package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
import org.signal.donations.StripeIntentAccessor;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredential;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequestContext;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse;
import org.signal.libsignal.zkgroup.receipts.ReceiptSerial;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.subscriptions.SubscriptionLevels;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Job responsible for submitting ReceiptCredentialRequest objects to the server until
 * we get a response.
 */
public class BoostReceiptRequestResponseJob extends BaseJob {

  private static final String TAG = Log.tag(BoostReceiptRequestResponseJob.class);

  public static final String KEY = "BoostReceiptCredentialsSubmissionJob";

  private static final String BOOST_QUEUE = "BoostReceiptRedemption";
  private static final String GIFT_QUEUE = "GiftReceiptRedemption";

  private static final String DATA_REQUEST_BYTES     = "data.request.bytes";
  private static final String DATA_PAYMENT_INTENT_ID = "data.payment.intent.id";
  private static final String DATA_ERROR_SOURCE      = "data.error.source";
  private static final String DATA_BADGE_LEVEL       = "data.badge.level";

  private ReceiptCredentialRequestContext requestContext;

  private final DonationErrorSource donationErrorSource;
  private final String              paymentIntentId;
  private final long                badgeLevel;

  private static BoostReceiptRequestResponseJob createJob(String paymentIntentId, DonationErrorSource donationErrorSource, long badgeLevel) {
    return new BoostReceiptRequestResponseJob(
        new Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(donationErrorSource == DonationErrorSource.BOOST ? BOOST_QUEUE : GIFT_QUEUE)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build(),
        null,
        paymentIntentId,
        donationErrorSource,
        badgeLevel
    );
  }

  public static JobManager.Chain createJobChainForBoost(@NonNull String paymentIntentId) {
    BoostReceiptRequestResponseJob     requestReceiptJob                  = createJob(paymentIntentId, DonationErrorSource.BOOST, Long.parseLong(SubscriptionLevels.BOOST_LEVEL));
    DonationReceiptRedemptionJob       redeemReceiptJob                   = DonationReceiptRedemptionJob.createJobForBoost();
    RefreshOwnProfileJob               refreshOwnProfileJob               = RefreshOwnProfileJob.forBoost();
    MultiDeviceProfileContentUpdateJob multiDeviceProfileContentUpdateJob = new MultiDeviceProfileContentUpdateJob();

    return ApplicationDependencies.getJobManager()
                                  .startChain(requestReceiptJob)
                                  .then(redeemReceiptJob)
                                  .then(refreshOwnProfileJob)
                                  .then(multiDeviceProfileContentUpdateJob);
  }

  public static JobManager.Chain createJobChainForGift(@NonNull String paymentIntentId,
                                                       @NonNull RecipientId recipientId,
                                                       @Nullable String additionalMessage,
                                                       long badgeLevel)
  {
    BoostReceiptRequestResponseJob requestReceiptJob = createJob(paymentIntentId, DonationErrorSource.GIFT, badgeLevel);
    GiftSendJob                    giftSendJob       = new GiftSendJob(recipientId, additionalMessage);


    return ApplicationDependencies.getJobManager()
                                  .startChain(requestReceiptJob)
                                  .then(giftSendJob);
  }

  private BoostReceiptRequestResponseJob(@NonNull Parameters parameters,
                                         @Nullable ReceiptCredentialRequestContext requestContext,
                                         @NonNull String paymentIntentId,
                                         @NonNull DonationErrorSource donationErrorSource,
                                         long badgeLevel)
  {
    super(parameters);
    this.requestContext      = requestContext;
    this.paymentIntentId     = paymentIntentId;
    this.donationErrorSource = donationErrorSource;
    this.badgeLevel          = badgeLevel;
  }

  @Override
  public @NonNull Data serialize() {
    Data.Builder builder = new Data.Builder().putString(DATA_PAYMENT_INTENT_ID, paymentIntentId)
                                             .putString(DATA_ERROR_SOURCE, donationErrorSource.serialize())
                                             .putLong(DATA_BADGE_LEVEL, badgeLevel);

    if (requestContext != null) {
      builder.putBlobAsString(DATA_REQUEST_BYTES, requestContext.serialize());
    }

    return builder.build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  @Override
  protected void onRun() throws Exception {
    if (requestContext == null) {
      Log.d(TAG, "Creating request context..");

      SecureRandom secureRandom = new SecureRandom();
      byte[]       randomBytes  = new byte[ReceiptSerial.SIZE];

      secureRandom.nextBytes(randomBytes);

      ReceiptSerial             receiptSerial = new ReceiptSerial(randomBytes);
      ClientZkReceiptOperations operations    = ApplicationDependencies.getClientZkReceiptOperations();

      requestContext = operations.createReceiptCredentialRequestContext(secureRandom, receiptSerial);
    } else {
      Log.d(TAG, "Reusing request context from previous run", true);
    }

    Log.d(TAG, "Submitting credential to server", true);
    ServiceResponse<ReceiptCredentialResponse> response = ApplicationDependencies.getDonationsService()
                                                                                 .submitBoostReceiptCredentialRequestSync(paymentIntentId, requestContext.getRequest());

    if (response.getApplicationError().isPresent()) {
      handleApplicationError(context, response, donationErrorSource);
    } else if (response.getResult().isPresent()) {
      ReceiptCredential receiptCredential = getReceiptCredential(response.getResult().get());

      if (!isCredentialValid(receiptCredential)) {
        DonationError.routeDonationError(context, DonationError.badgeCredentialVerificationFailure(donationErrorSource));
        throw new IOException("Could not validate receipt credential");
      }

      Log.d(TAG, "Validated credential. Handing off to next job.", true);
      ReceiptCredentialPresentation receiptCredentialPresentation = getReceiptCredentialPresentation(receiptCredential);
      setOutputData(new Data.Builder().putBlobAsString(DonationReceiptRedemptionJob.INPUT_RECEIPT_CREDENTIAL_PRESENTATION,
                                                       receiptCredentialPresentation.serialize())
                                      .build());
    } else {
      Log.w(TAG, "Encountered a retryable exception: " + response.getStatus(), response.getExecutionError().orElse(null), true);
      throw new RetryableException();
    }
  }

  private static void handleApplicationError(Context context, ServiceResponse<ReceiptCredentialResponse> response, @NonNull DonationErrorSource donationErrorSource) throws Exception {
    Throwable applicationException = response.getApplicationError().get();
    switch (response.getStatus()) {
      case 204:
        Log.w(TAG, "User payment not be completed yet.", applicationException, true);
        throw new RetryableException();
      case 400:
        Log.w(TAG, "Receipt credential request failed to validate.", applicationException, true);
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(donationErrorSource));
        throw new Exception(applicationException);
      case 402:
        Log.w(TAG, "User payment failed.", applicationException, true);
        DonationError.routeDonationError(context, DonationError.genericPaymentFailure(donationErrorSource));
        throw new Exception(applicationException);
      case 409:
        Log.w(TAG, "Receipt already redeemed with a different request credential.", response.getApplicationError().get(), true);
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(donationErrorSource));
        throw new Exception(applicationException);
      default:
        Log.w(TAG, "Encountered a server failure: " + response.getStatus(), applicationException, true);
        throw new RetryableException();
    }
  }

  private ReceiptCredentialPresentation getReceiptCredentialPresentation(@NonNull ReceiptCredential receiptCredential) throws RetryableException {
    ClientZkReceiptOperations operations = ApplicationDependencies.getClientZkReceiptOperations();

    try {
      return operations.createReceiptCredentialPresentation(receiptCredential);
    } catch (VerificationFailedException e) {
      Log.w(TAG, "getReceiptCredentialPresentation: encountered a verification failure in zk", e, true);
      requestContext = null;
      throw new RetryableException();
    }
  }

  private ReceiptCredential getReceiptCredential(@NonNull ReceiptCredentialResponse response) throws RetryableException {
    ClientZkReceiptOperations operations = ApplicationDependencies.getClientZkReceiptOperations();

    try {
      return operations.receiveReceiptCredential(requestContext, response);
    } catch (VerificationFailedException e) {
      Log.w(TAG, "getReceiptCredential: encountered a verification failure in zk", e, true);
      requestContext = null;
      throw new RetryableException();
    }
  }

  /**
   * Checks that the generated Receipt Credential has the following characteristics
   * - level should match the current subscription level and be the same level you signed up for at the time the subscription was last updated
   * - expiration time should have the following characteristics:
   * - expiration_time mod 86400 == 0
   * - expiration_time is between now and 90 days from now
   */
  private boolean isCredentialValid(@NonNull ReceiptCredential receiptCredential) {
    long    now                     = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    long    maxExpirationTime       = now + TimeUnit.DAYS.toSeconds(90);
    boolean isCorrectLevel          = receiptCredential.getReceiptLevel() == badgeLevel;
    boolean isExpiration86400       = receiptCredential.getReceiptExpirationTime() % 86400 == 0;
    boolean isExpirationInTheFuture = receiptCredential.getReceiptExpirationTime() > now;
    boolean isExpirationWithinMax   = receiptCredential.getReceiptExpirationTime() <= maxExpirationTime;

    Log.d(TAG, "Credential validation: isCorrectLevel(" + isCorrectLevel + " actual: " + receiptCredential.getReceiptLevel() + ", expected: " + badgeLevel +
               ") isExpiration86400(" + isExpiration86400 +
               ") isExpirationInTheFuture(" + isExpirationInTheFuture +
               ") isExpirationWithinMax(" + isExpirationWithinMax + ")", true);

    return isCorrectLevel && isExpiration86400 && isExpirationInTheFuture && isExpirationWithinMax;
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryableException;
  }

  @VisibleForTesting final static class RetryableException extends Exception {
  }

  public static class Factory implements Job.Factory<BoostReceiptRequestResponseJob> {
    @Override
    public @NonNull BoostReceiptRequestResponseJob create(@NonNull Parameters parameters, @NonNull Data data) {
      String              paymentIntentId     = data.getString(DATA_PAYMENT_INTENT_ID);
      DonationErrorSource donationErrorSource = DonationErrorSource.deserialize(data.getStringOrDefault(DATA_ERROR_SOURCE, DonationErrorSource.BOOST.serialize()));
      long                badgeLevel          = data.getLongOrDefault(DATA_BADGE_LEVEL, Long.parseLong(SubscriptionLevels.BOOST_LEVEL));

      try {
        if (data.hasString(DATA_REQUEST_BYTES)) {
          byte[]                          blob           = data.getStringAsBlob(DATA_REQUEST_BYTES);
          ReceiptCredentialRequestContext requestContext = new ReceiptCredentialRequestContext(blob);

          return new BoostReceiptRequestResponseJob(parameters, requestContext, paymentIntentId, donationErrorSource, badgeLevel);
        } else {
          return new BoostReceiptRequestResponseJob(parameters, null, paymentIntentId, donationErrorSource, badgeLevel);
        }
      } catch (InvalidInputException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
