package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
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
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.subscription.Subscriber;
import org.thoughtcrime.securesms.subscription.DonorBadgeNotifications;
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
public class SubscriptionReceiptRequestResponseJob extends BaseJob {

  private static final String TAG = Log.tag(SubscriptionReceiptRequestResponseJob.class);

  public static final String KEY = "SubscriptionReceiptCredentialsSubmissionJob";

  private static final String DATA_REQUEST_BYTES   = "data.request.bytes";
  private static final String DATA_SUBSCRIBER_ID   = "data.subscriber.id";

  public static final Object MUTEX = new Object();

  private ReceiptCredentialRequestContext requestContext;

  private final SubscriberId subscriberId;

  static SubscriptionReceiptRequestResponseJob createJob(SubscriberId subscriberId) {
    return new SubscriptionReceiptRequestResponseJob(
        new Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("ReceiptRedemption")
            .setMaxInstancesForQueue(1)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build(),
        null,
        subscriberId
    );
  }

  public static JobManager.Chain createSubscriptionContinuationJobChain() {
    Subscriber                            subscriber           = SignalStore.donationsValues().requireSubscriber();
    SubscriptionReceiptRequestResponseJob requestReceiptJob    = createJob(subscriber.getSubscriberId());
    DonationReceiptRedemptionJob          redeemReceiptJob     = DonationReceiptRedemptionJob.createJobForSubscription();
    RefreshOwnProfileJob                  refreshOwnProfileJob = RefreshOwnProfileJob.forSubscription();

    return ApplicationDependencies.getJobManager()
                                  .startChain(requestReceiptJob)
                                  .then(redeemReceiptJob)
                                  .then(refreshOwnProfileJob);
  }

  private SubscriptionReceiptRequestResponseJob(@NonNull Parameters parameters,
                                                @Nullable ReceiptCredentialRequestContext requestContext,
                                                @NonNull SubscriberId subscriberId)
  {
    super(parameters);
    this.requestContext = requestContext;
    this.subscriberId   = subscriberId;
  }

  @Override
  public @NonNull Data serialize() {
    Data.Builder builder = new Data.Builder().putBlobAsString(DATA_SUBSCRIBER_ID, subscriberId.getBytes());

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
    DonorBadgeNotifications.RedemptionFailed.INSTANCE.show(context);
    SignalStore.donationsValues().markSubscriptionRedemptionFailed();
  }

  @Override
  protected void onRun() throws Exception {
    synchronized (MUTEX) {
      doRun();
    }
  }

  private void doRun() throws Exception {
    ActiveSubscription.Subscription subscription = getLatestSubscriptionInformation();
    if (subscription == null) {
      Log.w(TAG, "Subscription is null.", true);
      throw new RetryableException();
    } else if (subscription.isFailedPayment()) {
      Log.w(TAG, "Subscription payment failure in active subscription response (status = " + subscription.getStatus() + "). Passing through to redemption job.", true);
      onPaymentFailure();
      return;
    } else if (!subscription.isActive()) {
      Log.w(TAG, "Subscription is not yet active. Status: " + subscription.getStatus(), true);
      throw new RetryableException();
    } else {
      Log.i(TAG, "Recording end of period from active subscription.", true);
      SignalStore.donationsValues().setLastEndOfPeriod(subscription.getEndOfCurrentPeriod());
    }

    if (requestContext == null) {
      Log.d(TAG, "Generating request credentials context for token redemption...", true);
      SecureRandom secureRandom = new SecureRandom();
      byte[]       randomBytes  = new byte[ReceiptSerial.SIZE];

      secureRandom.nextBytes(randomBytes);

      ReceiptSerial             receiptSerial = new ReceiptSerial(randomBytes);
      ClientZkReceiptOperations operations    = ApplicationDependencies.getClientZkReceiptOperations();

      requestContext = operations.createReceiptCredentialRequestContext(secureRandom, receiptSerial);
    } else {
      Log.d(TAG, "Already have credentials generated for this request. Retrying web service call...", true);
    }

    Log.d(TAG, "Submitting receipt credential request.");
    ServiceResponse<ReceiptCredentialResponse> response = ApplicationDependencies.getDonationsService()
                                                                                 .submitReceiptCredentialRequest(subscriberId, requestContext.getRequest())
                                                                                 .blockingGet();

    if (response.getApplicationError().isPresent()) {
      handleApplicationError(response);
    } else if (response.getResult().isPresent()) {
      ReceiptCredential receiptCredential = getReceiptCredential(response.getResult().get());

      if (!isCredentialValid(subscription, receiptCredential)) {
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

  private @Nullable ActiveSubscription.Subscription getLatestSubscriptionInformation() throws Exception {
    ServiceResponse<ActiveSubscription> activeSubscription = ApplicationDependencies.getDonationsService()
                                                                                    .getSubscription(subscriberId)
                                                                                    .blockingGet();

    if (activeSubscription.getResult().isPresent()) {
      return activeSubscription.getResult().get().getActiveSubscription();
    } else if (activeSubscription.getApplicationError().isPresent()) {
      Log.w(TAG, "Unrecoverable error getting the user's current subscription. Failing.", activeSubscription.getApplicationError().get(), true);
      throw new IOException(activeSubscription.getApplicationError().get());
    } else {
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

  private void handleApplicationError(ServiceResponse<ReceiptCredentialResponse> response) throws Exception {
    switch (response.getStatus()) {
      case 204:
        Log.w(TAG, "Payment is still processing. Trying again.", response.getApplicationError().get(), true);
        SignalStore.donationsValues().clearSubscriptionRedemptionFailed();
        throw new RetryableException();
      case 400:
        Log.w(TAG, "Receipt credential request failed to validate.", response.getApplicationError().get(), true);
        throw new Exception(response.getApplicationError().get());
      case 402:
        Log.w(TAG, "Subscription payment failure in credential response. Passing through to redemption job.", true);
        onPaymentFailure();
        break;
      case 403:
        Log.w(TAG, "SubscriberId password mismatch or account auth was present.", response.getApplicationError().get(), true);
        throw new Exception(response.getApplicationError().get());
      case 404:
        Log.w(TAG, "SubscriberId not found or misformed.", response.getApplicationError().get(), true);
        throw new Exception(response.getApplicationError().get());
      case 409:
        Log.w(TAG, "Latest paid receipt on subscription already redeemed with a different request credential.", response.getApplicationError().get(), true);
        throw new Exception(response.getApplicationError().get());
      default:
        Log.w(TAG, "Encountered a server failure response: " + response.getStatus(), response.getApplicationError().get(), true);
        throw new RetryableException();
    }
  }

  private void onPaymentFailure() {
    SignalStore.donationsValues().setShouldCancelSubscriptionBeforeNextSubscribeAttempt(true);
    setOutputData(new Data.Builder().putBoolean(DonationReceiptRedemptionJob.INPUT_PAYMENT_FAILURE, true).build());
  }

  /**
   * Checks that the generated Receipt Credential has the following characteristics
   * - level should match the current subscription level and be the same level you signed up for at the time the subscription was last updated
   * - expiration time should have the following characteristics:
   * - expiration_time mod 86400 == 0
   * - expiration_time is between now and 60 days from now
   */
  private boolean isCredentialValid(@NonNull ActiveSubscription.Subscription subscription, @NonNull ReceiptCredential receiptCredential) {
    long    now                     = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    long    maxExpirationTime       = now + TimeUnit.DAYS.toSeconds(60);
    boolean isSameLevel             = subscription.getLevel() == receiptCredential.getReceiptLevel();
    boolean isExpirationAfterSub    = subscription.getEndOfCurrentPeriod() < receiptCredential.getReceiptExpirationTime();
    boolean isExpiration86400       = receiptCredential.getReceiptExpirationTime() % 86400 == 0;
    boolean isExpirationInTheFuture = receiptCredential.getReceiptExpirationTime() > now;
    boolean isExpirationWithinMax   = receiptCredential.getReceiptExpirationTime() <= maxExpirationTime;

    Log.d(TAG, "Credential validation: isSameLevel(" + isSameLevel +
               ") isExpirationAfterSub(" + isExpirationAfterSub +
               ") isExpiration86400(" + isExpiration86400 +
               ") isExpirationInTheFuture(" + isExpirationInTheFuture +
               ") isExpirationWithinMax(" + isExpirationWithinMax + ")", true);

    return isSameLevel && isExpirationAfterSub && isExpiration86400 && isExpirationInTheFuture && isExpirationWithinMax;
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryableException;
  }

  @VisibleForTesting
  final static class RetryableException extends Exception {
  }

  public static class Factory implements Job.Factory<SubscriptionReceiptRequestResponseJob> {
    @Override
    public @NonNull SubscriptionReceiptRequestResponseJob create(@NonNull Parameters parameters, @NonNull Data data) {
      SubscriberId subscriberId = SubscriberId.fromBytes(data.getStringAsBlob(DATA_SUBSCRIBER_ID));

      try {
        if (data.hasString(DATA_REQUEST_BYTES)) {
          byte[]                          blob           = data.getStringAsBlob(DATA_REQUEST_BYTES);
          ReceiptCredentialRequestContext requestContext = new ReceiptCredentialRequestContext(blob);

          return new SubscriptionReceiptRequestResponseJob(parameters, requestContext, subscriberId);
        } else {
          return new SubscriptionReceiptRequestResponseJob(parameters, null, subscriberId);
        }
      } catch (InvalidInputException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
