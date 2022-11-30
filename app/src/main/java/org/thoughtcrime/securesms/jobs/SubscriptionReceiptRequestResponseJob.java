package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
import org.signal.donations.PaymentSourceType;
import org.signal.donations.StripeDeclineCode;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredential;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequestContext;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.subscription.Subscriber;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.api.subscriptions.SubscriberId;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Job responsible for submitting ReceiptCredentialRequest objects to the server until
 * we get a response.
 */
public class SubscriptionReceiptRequestResponseJob extends BaseJob {

  private static final String TAG = Log.tag(SubscriptionReceiptRequestResponseJob.class);

  public static final String KEY = "SubscriptionReceiptCredentialsSubmissionJob";

  private static final String DATA_REQUEST_BYTES     = "data.request.bytes";
  private static final String DATA_SUBSCRIBER_ID     = "data.subscriber.id";
  private static final String DATA_IS_FOR_KEEP_ALIVE = "data.is.for.keep.alive";

  public static final Object MUTEX = new Object();

  private final SubscriberId subscriberId;
  private final boolean      isForKeepAlive;

  private static SubscriptionReceiptRequestResponseJob createJob(SubscriberId subscriberId, boolean isForKeepAlive) {
    return new SubscriptionReceiptRequestResponseJob(
        new Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("ReceiptRedemption")
            .setMaxInstancesForQueue(1)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build(),
        subscriberId,
        isForKeepAlive
    );
  }

  public static JobManager.Chain createSubscriptionContinuationJobChain() {
    return createSubscriptionContinuationJobChain(false);
  }

  public static JobManager.Chain createSubscriptionContinuationJobChain(boolean isForKeepAlive) {
    Subscriber                            subscriber                         = SignalStore.donationsValues().requireSubscriber();
    SubscriptionReceiptRequestResponseJob requestReceiptJob                  = createJob(subscriber.getSubscriberId(), isForKeepAlive);
    DonationReceiptRedemptionJob          redeemReceiptJob                   = DonationReceiptRedemptionJob.createJobForSubscription(requestReceiptJob.getErrorSource());
    RefreshOwnProfileJob                  refreshOwnProfileJob               = RefreshOwnProfileJob.forSubscription();
    MultiDeviceProfileContentUpdateJob    multiDeviceProfileContentUpdateJob = new MultiDeviceProfileContentUpdateJob();

    return ApplicationDependencies.getJobManager()
                                  .startChain(requestReceiptJob)
                                  .then(redeemReceiptJob)
                                  .then(refreshOwnProfileJob)
                                  .then(multiDeviceProfileContentUpdateJob);
  }

  private SubscriptionReceiptRequestResponseJob(@NonNull Parameters parameters,
                                                @NonNull SubscriberId subscriberId,
                                                boolean isForKeepAlive)
  {
    super(parameters);
    this.subscriberId   = subscriberId;
    this.isForKeepAlive = isForKeepAlive;
  }

  @Override
  public @NonNull Data serialize() {
    Data.Builder builder = new Data.Builder().putBlobAsString(DATA_SUBSCRIBER_ID, subscriberId.getBytes())
                                             .putBoolean(DATA_IS_FOR_KEEP_ALIVE, isForKeepAlive);

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
    synchronized (MUTEX) {
      doRun();
    }
  }

  private void doRun() throws Exception {
    ReceiptCredentialRequestContext requestContext     = SignalStore.donationsValues().getSubscriptionRequestCredential();
    ActiveSubscription              activeSubscription = getLatestSubscriptionInformation();
    ActiveSubscription.Subscription subscription       = activeSubscription.getActiveSubscription();

    if (requestContext == null) {
      Log.w(TAG, "Request context is null.", true);
      throw new Exception("Cannot get a response without a request.");
    }

    if (subscription == null) {
      Log.w(TAG, "Subscription is null.", true);
      throw new RetryableException();
    } else if (subscription.isFailedPayment()) {
      ActiveSubscription.ChargeFailure chargeFailure = activeSubscription.getChargeFailure();
      if (chargeFailure != null) {
        Log.w(TAG, "Subscription payment charge failure code: " + chargeFailure.getCode() + ", message: " + chargeFailure.getMessage(), true);
      }

      if (isForKeepAlive) {
        Log.w(TAG, "Subscription payment failure in active subscription response (status = " + subscription.getStatus() + ").", true);
        onPaymentFailure(subscription.getStatus(), chargeFailure, subscription.getEndOfCurrentPeriod(), true);
        throw new Exception("Active subscription hit a payment failure: " + subscription.getStatus());
      } else {
        Log.w(TAG, "New subscription has hit a payment failure. (status = " + subscription.getStatus() + ").", true);
        onPaymentFailure(subscription.getStatus(), chargeFailure, subscription.getEndOfCurrentPeriod(), false);
        throw new Exception("New subscription has hit a payment failure: " + subscription.getStatus());
      }
    } else if (!subscription.isActive()) {
      ActiveSubscription.ChargeFailure chargeFailure = activeSubscription.getChargeFailure();
      if (chargeFailure != null) {
        Log.w(TAG, "Subscription payment charge failure code: " + chargeFailure.getCode() + ", message: " + chargeFailure.getMessage(), true);

        if (!isForKeepAlive) {
          Log.w(TAG, "Initial subscription payment failed, treating as a permanent failure.");
          onPaymentFailure(subscription.getStatus(), chargeFailure, subscription.getEndOfCurrentPeriod(), false);
          throw new Exception("New subscription has hit a payment failure.");
        }
      }

      Log.w(TAG, "Subscription is not yet active. Status: " + subscription.getStatus(), true);
      throw new RetryableException();
    } else if (subscription.isCanceled()) {
      Log.w(TAG, "Subscription is marked as cancelled, but it's possible that the user cancelled and then later tried to resubscribe. Scheduling a retry.", true);
      throw new RetryableException();
    } else {
      Log.i(TAG, "Subscription is valid, proceeding with request for ReceiptCredentialResponse", true);
      long storedEndOfPeriod = SignalStore.donationsValues().getLastEndOfPeriod();
      if (storedEndOfPeriod < subscription.getEndOfCurrentPeriod()) {
        Log.i(TAG, "Storing lastEndOfPeriod and syncing with linked devices", true);
        SignalStore.donationsValues().setLastEndOfPeriod(subscription.getEndOfCurrentPeriod());
        MultiDeviceSubscriptionSyncRequestJob.enqueue();
      }

      if (SignalStore.donationsValues().getSubscriptionEndOfPeriodConversionStarted() == 0L) {
        Log.i(TAG, "Marking the start of initial conversion.", true);
        SignalStore.donationsValues().setSubscriptionEndOfPeriodConversionStarted(subscription.getEndOfCurrentPeriod());
      }
    }

    Log.d(TAG, "Submitting receipt credential request.");
    ServiceResponse<ReceiptCredentialResponse> response = ApplicationDependencies.getDonationsService()
                                                                                 .submitReceiptCredentialRequestSync(subscriberId, requestContext.getRequest());

    if (response.getApplicationError().isPresent()) {
      handleApplicationError(response);
    } else if (response.getResult().isPresent()) {
      ReceiptCredential receiptCredential = getReceiptCredential(requestContext, response.getResult().get());

      if (!isCredentialValid(subscription, receiptCredential)) {
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(getErrorSource()));
        throw new IOException("Could not validate receipt credential");
      }

      ReceiptCredentialPresentation receiptCredentialPresentation = getReceiptCredentialPresentation(receiptCredential);

      Log.d(TAG, "Validated credential. Recording receipt and handing off to redemption job.", true);
      SignalDatabase.donationReceipts().addReceipt(DonationReceiptRecord.createForSubscription(subscription));

      SignalStore.donationsValues().clearSubscriptionRequestCredential();
      SignalStore.donationsValues().setSubscriptionReceiptCredential(receiptCredentialPresentation);
      SignalStore.donationsValues().setSubscriptionEndOfPeriodRedemptionStarted(subscription.getEndOfCurrentPeriod());
    } else {
      Log.w(TAG, "Encountered a retryable exception: " + response.getStatus(), response.getExecutionError().orElse(null), true);
      throw new RetryableException();
    }
  }

  private @NonNull ActiveSubscription getLatestSubscriptionInformation() throws Exception {
    ServiceResponse<ActiveSubscription> activeSubscription = ApplicationDependencies.getDonationsService()
                                                                                    .getSubscription(subscriberId);

    if (activeSubscription.getResult().isPresent()) {
      return activeSubscription.getResult().get();
    } else if (activeSubscription.getApplicationError().isPresent()) {
      Log.w(TAG, "Unrecoverable error getting the user's current subscription. Failing.", activeSubscription.getApplicationError().get(), true);
      DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(getErrorSource()));
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
      throw new RetryableException();
    }
  }

  private ReceiptCredential getReceiptCredential(@NonNull ReceiptCredentialRequestContext requestContext, @NonNull ReceiptCredentialResponse response) throws RetryableException {
    ClientZkReceiptOperations operations = ApplicationDependencies.getClientZkReceiptOperations();

    try {
      return operations.receiveReceiptCredential(requestContext, response);
    } catch (VerificationFailedException e) {
      Log.w(TAG, "getReceiptCredential: encountered a verification failure in zk", e, true);
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
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(getErrorSource()));
        throw new Exception(response.getApplicationError().get());
      case 402:
        Log.w(TAG, "Payment looks like a failure but may be retried.", response.getApplicationError().get(), true);
        throw new RetryableException();
      case 403:
        Log.w(TAG, "SubscriberId password mismatch or account auth was present.", response.getApplicationError().get(), true);
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(getErrorSource()));
        throw new Exception(response.getApplicationError().get());
      case 404:
        Log.w(TAG, "SubscriberId not found or misformed.", response.getApplicationError().get(), true);
        DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(getErrorSource()));
        throw new Exception(response.getApplicationError().get());
      case 409:
        onAlreadyRedeemed(response);
        break;
      default:
        Log.w(TAG, "Encountered a server failure response: " + response.getStatus(), response.getApplicationError().get(), true);
        throw new RetryableException();
    }
  }

  /**
   * Handles state updates and error routing for a payment failure.
   * <p>
   * There are two ways this could go, depending on whether the job was created for a keep-alive chain.
   * <p>
   * 1. In the case of a normal chain (new subscription) We simply route the error out to the user. The payment failure would have occurred while trying to
   * charge for the first month of their subscription, and are likely still on the "Subscribe" screen, so we can just display a dialog.
   * 1. In the case of a keep-alive event, we want to book-keep the error to show the user on a subsequent launch, and we want to sync our failure state to
   * linked devices.
   */
  private void onPaymentFailure(@NonNull String status, @Nullable ActiveSubscription.ChargeFailure chargeFailure, long timestamp, boolean isForKeepAlive) {
    SignalStore.donationsValues().setShouldCancelSubscriptionBeforeNextSubscribeAttempt(true);
    if (isForKeepAlive) {
      Log.d(TAG, "Is for a keep-alive and we have a status. Setting UnexpectedSubscriptionCancelation state...", true);
      SignalStore.donationsValues().setUnexpectedSubscriptionCancelationChargeFailure(chargeFailure);
      SignalStore.donationsValues().setUnexpectedSubscriptionCancelationReason(status);
      SignalStore.donationsValues().setUnexpectedSubscriptionCancelationTimestamp(timestamp);
      MultiDeviceSubscriptionSyncRequestJob.enqueue();
    } else if (chargeFailure != null) {
      Log.d(TAG, "Charge failure detected: " + chargeFailure, true);

      StripeDeclineCode               declineCode = StripeDeclineCode.Companion.getFromCode(chargeFailure.getOutcomeNetworkReason());
      DonationError.PaymentSetupError paymentSetupError;
      PaymentSourceType               paymentSourceType = SignalStore.donationsValues().getSubscriptionPaymentSourceType();
      boolean                         isStripeSource = paymentSourceType instanceof PaymentSourceType.Stripe;

      if (declineCode.isKnown() && isStripeSource) {
        paymentSetupError = new DonationError.PaymentSetupError.StripeDeclinedError(
            getErrorSource(),
            new Exception(chargeFailure.getMessage()),
            declineCode,
            (PaymentSourceType.Stripe) paymentSourceType
        );
      } else if (isStripeSource) {
        paymentSetupError = new DonationError.PaymentSetupError.StripeCodedError(
            getErrorSource(),
            new Exception("Card was declined. " + chargeFailure.getCode()),
            chargeFailure.getCode()
        );
      } else {
        paymentSetupError = new DonationError.PaymentSetupError.GenericError(
            getErrorSource(),
            new Exception("Payment Failed for " + paymentSourceType.getCode())
        );
      }

      Log.w(TAG, "Not for a keep-alive and we have a charge failure. Routing a payment setup error...", true);
      DonationError.routeDonationError(context, paymentSetupError);
    } else {
      Log.d(TAG, "Not for a keep-alive and we have a failure status. Routing a payment setup error...", true);
      DonationError.routeDonationError(context, new DonationError.PaymentSetupError.GenericError(
          getErrorSource(),
          new Exception("Got a failure status from the subscription object.")
      ));
    }
  }

  /**
   * Handle 409 error code. This is a permanent failure for new subscriptions but an ignorable error for keep-alive messages.
   */
  private void onAlreadyRedeemed(ServiceResponse<ReceiptCredentialResponse> response) throws Exception {
    if (isForKeepAlive) {
      Log.i(TAG, "KeepAlive: Latest paid receipt on subscription already redeemed with a different request credential, ignoring.", response.getApplicationError().get(), true);
      setOutputData(new Data.Builder().putBoolean(DonationReceiptRedemptionJob.INPUT_KEEP_ALIVE_409, true).build());
    } else {
      Log.w(TAG, "Latest paid receipt on subscription already redeemed with a different request credential.", response.getApplicationError().get(), true);
      DonationError.routeDonationError(context, DonationError.genericBadgeRedemptionFailure(getErrorSource()));
      throw new Exception(response.getApplicationError().get());
    }
  }

  private DonationErrorSource getErrorSource() {
    return isForKeepAlive ? DonationErrorSource.KEEP_ALIVE : DonationErrorSource.SUBSCRIPTION;
  }

  /**
   * Checks that the generated Receipt Credential has the following characteristics
   * - level should match the current subscription level and be the same level you signed up for at the time the subscription was last updated
   * - expiration time should have the following characteristics:
   * - expiration_time mod 86400 == 0
   * - expiration_time is between now and 90 days from now
   */
  private static boolean isCredentialValid(@NonNull ActiveSubscription.Subscription subscription, @NonNull ReceiptCredential receiptCredential) {
    long    now                     = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    long    maxExpirationTime       = now + TimeUnit.DAYS.toSeconds(90);
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

  @VisibleForTesting final static class RetryableException extends Exception {
  }

  public static class Factory implements Job.Factory<SubscriptionReceiptRequestResponseJob> {
    @Override
    public @NonNull SubscriptionReceiptRequestResponseJob create(@NonNull Parameters parameters, @NonNull Data data) {
      SubscriberId subscriberId        = SubscriberId.fromBytes(data.getStringAsBlob(DATA_SUBSCRIBER_ID));
      boolean      isForKeepAlive      = data.getBooleanOrDefault(DATA_IS_FOR_KEEP_ALIVE, false);
      String       requestString       = data.getStringOrDefault(DATA_REQUEST_BYTES, null);
      byte[]       requestContextBytes = requestString != null ? Base64.decodeOrThrow(requestString) : null;

      ReceiptCredentialRequestContext requestContext;
      if (requestContextBytes != null && SignalStore.donationsValues().getSubscriptionRequestCredential() == null) {
        try {
          requestContext = new ReceiptCredentialRequestContext(requestContextBytes);
          SignalStore.donationsValues().setSubscriptionRequestCredential(requestContext);
        } catch (InvalidInputException e) {
          Log.e(TAG, "Failed to generate request context from bytes", e);
          throw new AssertionError(e);
        }
      }

      return new SubscriptionReceiptRequestResponseJob(parameters, subscriberId, isForKeepAlive);
    }
  }
}
