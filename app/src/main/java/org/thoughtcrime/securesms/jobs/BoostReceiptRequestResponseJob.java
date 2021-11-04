package org.thoughtcrime.securesms.jobs;

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
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.subscription.SubscriptionNotification;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.api.subscriptions.SubscriberId;
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
            .setLifespan(TimeUnit.DAYS.toMillis(30))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build(),
        null,
        paymentIntent.getId()
    );
  }

  public static String enqueueChain(StripeApi.PaymentIntent paymentIntent) {
    BoostReceiptRequestResponseJob requestReceiptJob    = createJob(paymentIntent);
    DonationReceiptRedemptionJob   redeemReceiptJob     = DonationReceiptRedemptionJob.createJobForBoost();
    RefreshOwnProfileJob           refreshOwnProfileJob = new RefreshOwnProfileJob();

    ApplicationDependencies.getJobManager()
                           .startChain(requestReceiptJob)
                           .then(redeemReceiptJob)
                           .then(refreshOwnProfileJob)
                           .enqueue();

    return refreshOwnProfileJob.getId();
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
    SubscriptionNotification.VerificationFailed.INSTANCE.show(context);
  }

  @Override
  protected void onRun() throws Exception {
    if (requestContext == null) {
      SecureRandom secureRandom = new SecureRandom();
      byte[]       randomBytes  = new byte[ReceiptSerial.SIZE];

      secureRandom.nextBytes(randomBytes);

      ReceiptSerial             receiptSerial = new ReceiptSerial(randomBytes);
      ClientZkReceiptOperations operations    = ApplicationDependencies.getClientZkReceiptOperations();

      requestContext = operations.createReceiptCredentialRequestContext(secureRandom, receiptSerial);
    }

    ServiceResponse<ReceiptCredentialResponse> response = ApplicationDependencies.getDonationsService()
                                                                                 .submitBoostReceiptCredentialRequest(paymentIntentId, requestContext.getRequest())
                                                                                 .blockingGet();

    if (response.getApplicationError().isPresent()) {
      if (response.getStatus() == 204) {
        Log.w(TAG, "User does not have receipts available to exchange. Exiting.", response.getApplicationError().get());
      } else {
        Log.w(TAG, "Encountered a server failure: " + response.getStatus(), response.getApplicationError().get());
        throw new RetryableException();
      }
    } else if (response.getResult().isPresent()) {
      ReceiptCredential receiptCredential = getReceiptCredential(response.getResult().get());

      if (!isCredentialValid(receiptCredential)) {
        throw new IOException("Could not validate receipt credential");
      }

      ReceiptCredentialPresentation receiptCredentialPresentation = getReceiptCredentialPresentation(receiptCredential);
      setOutputData(new Data.Builder().putBlobAsString(DonationReceiptRedemptionJob.INPUT_RECEIPT_CREDENTIAL_PRESENTATION,
                                                       receiptCredentialPresentation.serialize())
                                      .build());
    } else {
      Log.w(TAG, "Encountered a retryable exception: " + response.getStatus(), response.getExecutionError().orNull());
      throw new RetryableException();
    }
  }

  private ReceiptCredentialPresentation getReceiptCredentialPresentation(@NonNull ReceiptCredential receiptCredential) throws RetryableException {
    ClientZkReceiptOperations operations = ApplicationDependencies.getClientZkReceiptOperations();

    try {
      return operations.createReceiptCredentialPresentation(receiptCredential);
    } catch (VerificationFailedException e) {
      Log.w(TAG, "getReceiptCredentialPresentation: encountered a verification failure in zk", e);
      requestContext = null;
      throw new RetryableException();
    }
  }

  private ReceiptCredential getReceiptCredential(@NonNull ReceiptCredentialResponse response) throws RetryableException {
    ClientZkReceiptOperations operations = ApplicationDependencies.getClientZkReceiptOperations();

    try {
      return operations.receiveReceiptCredential(requestContext, response);
    } catch (VerificationFailedException e) {
      Log.w(TAG, "getReceiptCredential: encountered a verification failure in zk", e);
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
    long    now                      = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    long    monthFromNow             = now + TimeUnit.DAYS.toSeconds(60);
    boolean isCorrectLevel           = receiptCredential.getReceiptLevel() == 1;
    boolean isExpiration86400        = receiptCredential.getReceiptExpirationTime() % 86400 == 0;
    boolean isExpirationInTheFuture  = receiptCredential.getReceiptExpirationTime() > now;
    boolean isExpirationWithinAMonth = receiptCredential.getReceiptExpirationTime() < monthFromNow;

    return isCorrectLevel && isExpiration86400 && isExpirationInTheFuture && isExpirationWithinAMonth;
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
