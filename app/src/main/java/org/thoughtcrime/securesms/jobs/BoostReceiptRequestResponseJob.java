package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
import org.signal.donations.StripeApi;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.receipts.ClientZkReceiptOperations;
import org.signal.zkgroup.receipts.ReceiptCredential;
import org.signal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.signal.zkgroup.receipts.ReceiptCredentialRequestContext;
import org.signal.zkgroup.receipts.ReceiptCredentialResponse;
import org.signal.zkgroup.receipts.ReceiptSerial;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
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

  private static final String DATA_REQUEST_BYTES     = "data.request.bytes";
  private static final String DATA_PAYMENT_INTENT_ID = "data.payment.intent.id";

  private ReceiptCredentialRequestContext requestContext;

  private final String paymentIntentId;

  static BoostReceiptRequestResponseJob createJob(StripeApi.PaymentIntent paymentIntent) {
    return new BoostReceiptRequestResponseJob(
        new Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("BoostReceiptRedemption")
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build(),
        null,
        paymentIntent.getId()
    );
  }

  public static JobManager.Chain createJobChain(StripeApi.PaymentIntent paymentIntent) {
    BoostReceiptRequestResponseJob requestReceiptJob    = createJob(paymentIntent);
    DonationReceiptRedemptionJob   redeemReceiptJob     = DonationReceiptRedemptionJob.createJobForBoost();
    RefreshOwnProfileJob           refreshOwnProfileJob = RefreshOwnProfileJob.forBoost();

    return ApplicationDependencies.getJobManager()
                                  .startChain(requestReceiptJob)
                                  .then(redeemReceiptJob)
                                  .then(refreshOwnProfileJob);
  }

  private BoostReceiptRequestResponseJob(@NonNull Parameters parameters,
                                         @Nullable ReceiptCredentialRequestContext requestContext,
                                         @NonNull String paymentIntentId)
  {
    super(parameters);
    this.requestContext  = requestContext;
    this.paymentIntentId = paymentIntentId;
  }

  @Override
  public @NonNull Data serialize() {
    Data.Builder builder = new Data.Builder().putString(DATA_PAYMENT_INTENT_ID, paymentIntentId);

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
                                                                                 .submitBoostReceiptCredentialRequest(paymentIntentId, requestContext.getRequest())
                                                                                 .blockingGet();

    if (response.getApplicationError().isPresent()) {
      handleApplicationError(context, response);
    } else if (response.getResult().isPresent()) {
      ReceiptCredential receiptCredential = getReceiptCredential(response.getResult().get());

      if (!isCredentialValid(receiptCredential)) {
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(DonationErrorSource.BOOST));
        throw new IOException("Could not validate receipt credential");
      }

      Log.d(TAG, "Validated credential. Handing off to redemption job.", true);
      ReceiptCredentialPresentation receiptCredentialPresentation = getReceiptCredentialPresentation(receiptCredential);
      setOutputData(new Data.Builder().putBlobAsString(DonationReceiptRedemptionJob.INPUT_RECEIPT_CREDENTIAL_PRESENTATION,
                                                       receiptCredentialPresentation.serialize())
                                      .build());
    } else {
      Log.w(TAG, "Encountered a retryable exception: " + response.getStatus(), response.getExecutionError().orNull(), true);
      throw new RetryableException();
    }
  }

  private static void handleApplicationError(Context context, ServiceResponse<ReceiptCredentialResponse> response) throws Exception {
    Throwable applicationException = response.getApplicationError().get();
    switch (response.getStatus()) {
      case 204:
        Log.w(TAG, "User payment not be completed yet.", applicationException, true);
        throw new RetryableException();
      case 400:
        Log.w(TAG, "Receipt credential request failed to validate.", applicationException, true);
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(DonationErrorSource.BOOST));
        throw new Exception(applicationException);
      case 402:
        Log.w(TAG, "User payment failed.", applicationException, true);
        DonationError.routeDonationError(context, DonationError.genericPaymentFailure(DonationErrorSource.BOOST));
        throw new Exception(applicationException);
      case 409:
        Log.w(TAG, "Receipt already redeemed with a different request credential.", response.getApplicationError().get(), true);
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(DonationErrorSource.BOOST));
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
   * - expiration_time is between now and 60 days from now
   */
  private boolean isCredentialValid(@NonNull ReceiptCredential receiptCredential) {
    long    now                     = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    long    maxExpirationTime       = now + TimeUnit.DAYS.toSeconds(60);
    boolean isCorrectLevel          = receiptCredential.getReceiptLevel() == 1;
    boolean isExpiration86400       = receiptCredential.getReceiptExpirationTime() % 86400 == 0;
    boolean isExpirationInTheFuture = receiptCredential.getReceiptExpirationTime() > now;
    boolean isExpirationWithinMax   = receiptCredential.getReceiptExpirationTime() <= maxExpirationTime;

    Log.d(TAG, "Credential validation: isCorrectLevel(" + isCorrectLevel +
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
      String paymentIntentId = data.getString(DATA_PAYMENT_INTENT_ID);

      try {
        if (data.hasString(DATA_REQUEST_BYTES)) {
          byte[]                          blob           = data.getStringAsBlob(DATA_REQUEST_BYTES);
          ReceiptCredentialRequestContext requestContext = new ReceiptCredentialRequestContext(blob);

          return new BoostReceiptRequestResponseJob(parameters, requestContext, paymentIntentId);
        } else {
          return new BoostReceiptRequestResponseJob(parameters, null, paymentIntentId);
        }
      } catch (InvalidInputException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
