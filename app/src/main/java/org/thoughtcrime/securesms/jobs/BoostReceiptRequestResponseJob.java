package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
import org.signal.donations.StripeDeclineCode;
import org.signal.donations.StripeFailureCode;
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
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue;
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.api.subscriptions.SubscriptionLevels;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.DonationProcessor;
import org.whispersystems.signalservice.internal.push.exceptions.InAppPaymentReceiptCredentialError;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import okio.ByteString;

/**
 * Job responsible for submitting ReceiptCredentialRequest objects to the server until
 * we get a response.
 *
 * @deprecated Replaced with InAppPaymentOneTimeContextJob
 */
@Deprecated
public class BoostReceiptRequestResponseJob extends BaseJob {

  private static final String TAG = Log.tag(BoostReceiptRequestResponseJob.class);

  public static final String KEY = "BoostReceiptCredentialsSubmissionJob";

  private static final String BOOST_QUEUE         = "BoostReceiptRedemption";
  private static final String GIFT_QUEUE          = "GiftReceiptRedemption";
  private static final String LONG_RUNNING_SUFFIX = "__LongRunning";

  private static final String DATA_REQUEST_BYTES      = "data.request.bytes";
  private static final String DATA_PAYMENT_INTENT_ID  = "data.payment.intent.id";
  private static final String DATA_ERROR_SOURCE       = "data.error.source";
  private static final String DATA_BADGE_LEVEL        = "data.badge.level";
  private static final String DATA_DONATION_PROCESSOR = "data.donation.processor";
  private static final String DATA_UI_SESSION_KEY     = "data.ui.session.key";
  private static final String DATA_TERMINAL_DONATION  = "data.terminal.donation";

  private ReceiptCredentialRequestContext        requestContext;
  private TerminalDonationQueue.TerminalDonation terminalDonation;


  private final DonationErrorSource donationErrorSource;
  private final String              paymentIntentId;
  private final long                badgeLevel;
  private final DonationProcessor   donationProcessor;
  private final long                uiSessionKey;

  private static String resolveQueue(DonationErrorSource donationErrorSource, boolean isLongRunning) {
    String baseQueue = donationErrorSource == DonationErrorSource.ONE_TIME ? BOOST_QUEUE : GIFT_QUEUE;
    return isLongRunning ? baseQueue + LONG_RUNNING_SUFFIX : baseQueue;
  }

  private static long resolveLifespan(boolean isLongRunning) {
    return isLongRunning ? TimeUnit.DAYS.toMillis(30) : TimeUnit.DAYS.toMillis(1);
  }

  private static BoostReceiptRequestResponseJob createJob(@NonNull String paymentIntentId,
                                                          @NonNull DonationErrorSource donationErrorSource,
                                                          long badgeLevel,
                                                          @NonNull DonationProcessor donationProcessor,
                                                          long uiSessionKey,
                                                          @NonNull TerminalDonationQueue.TerminalDonation terminalDonation)
  {
    return new BoostReceiptRequestResponseJob(
        new Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(resolveQueue(donationErrorSource, terminalDonation.isLongRunningPaymentMethod))
            .setLifespan(resolveLifespan(terminalDonation.isLongRunningPaymentMethod))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build(),
        null,
        paymentIntentId,
        donationErrorSource,
        badgeLevel,
        donationProcessor,
        uiSessionKey,
        terminalDonation
    );
  }

  public static JobManager.Chain createJobChainForBoost(@NonNull String paymentIntentId,
                                                        @NonNull DonationProcessor donationProcessor,
                                                        long uiSessionKey,
                                                        @NonNull TerminalDonationQueue.TerminalDonation terminalDonation)
  {
    BoostReceiptRequestResponseJob     requestReceiptJob                  = createJob(paymentIntentId, DonationErrorSource.ONE_TIME, Long.parseLong(SubscriptionLevels.BOOST_LEVEL), donationProcessor, uiSessionKey, terminalDonation);
    DonationReceiptRedemptionJob       redeemReceiptJob                   = DonationReceiptRedemptionJob.createJobForBoost(uiSessionKey, terminalDonation.isLongRunningPaymentMethod);
    RefreshOwnProfileJob               refreshOwnProfileJob               = RefreshOwnProfileJob.forBoost();
    MultiDeviceProfileContentUpdateJob multiDeviceProfileContentUpdateJob = new MultiDeviceProfileContentUpdateJob();

    return AppDependencies.getJobManager()
                          .startChain(requestReceiptJob)
                          .then(redeemReceiptJob)
                          .then(refreshOwnProfileJob)
                          .then(multiDeviceProfileContentUpdateJob);
  }

  public static JobManager.Chain createJobChainForGift(@NonNull String paymentIntentId,
                                                       @NonNull RecipientId recipientId,
                                                       @Nullable String additionalMessage,
                                                       long badgeLevel,
                                                       @NonNull DonationProcessor donationProcessor,
                                                       long uiSessionKey,
                                                       @NonNull TerminalDonationQueue.TerminalDonation terminalDonation)
  {
    BoostReceiptRequestResponseJob requestReceiptJob = createJob(paymentIntentId, DonationErrorSource.GIFT, badgeLevel, donationProcessor, uiSessionKey, terminalDonation);
    GiftSendJob                    giftSendJob       = new GiftSendJob(recipientId, additionalMessage);


    return AppDependencies.getJobManager()
                          .startChain(requestReceiptJob)
                          .then(giftSendJob);
  }

  private BoostReceiptRequestResponseJob(@NonNull Parameters parameters,
                                         @Nullable ReceiptCredentialRequestContext requestContext,
                                         @NonNull String paymentIntentId,
                                         @NonNull DonationErrorSource donationErrorSource,
                                         long badgeLevel,
                                         @NonNull DonationProcessor donationProcessor,
                                         long uiSessionKey,
                                         @NonNull TerminalDonationQueue.TerminalDonation terminalDonation)
  {
    super(parameters);
    this.requestContext      = requestContext;
    this.paymentIntentId     = paymentIntentId;
    this.donationErrorSource = donationErrorSource;
    this.badgeLevel          = badgeLevel;
    this.donationProcessor   = donationProcessor;
    this.uiSessionKey        = uiSessionKey;
    this.terminalDonation    = terminalDonation;
  }

  @Override
  public @Nullable byte[] serialize() {
    JsonJobData.Builder builder = new JsonJobData.Builder().putString(DATA_PAYMENT_INTENT_ID, paymentIntentId)
                                                           .putString(DATA_ERROR_SOURCE, donationErrorSource.serialize())
                                                           .putLong(DATA_BADGE_LEVEL, badgeLevel)
                                                           .putString(DATA_DONATION_PROCESSOR, donationProcessor.getCode())
                                                           .putLong(DATA_UI_SESSION_KEY, uiSessionKey)
                                                           .putBlobAsString(DATA_TERMINAL_DONATION, terminalDonation.encode());

    if (requestContext != null) {
      builder.putBlobAsString(DATA_REQUEST_BYTES, requestContext.serialize());
    }

    return builder.serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
    if (terminalDonation.error != null) {
      SignalStore.inAppPayments().appendToTerminalDonationQueue(terminalDonation);
    } else {
      Log.w(TAG, "Job is in terminal state without an error on TerminalDonation.");
    }
  }

  @Override
  public long getNextRunAttemptBackoff(int pastAttemptCount, @NonNull Exception exception) {
    if (terminalDonation.isLongRunningPaymentMethod) {
      return TimeUnit.DAYS.toMillis(1);
    } else {
      return super.getNextRunAttemptBackoff(pastAttemptCount, exception);
    }
  }

  @Override
  protected void onRun() throws Exception {
    if (requestContext == null) {
      Log.d(TAG, "Creating request context..");

      SecureRandom secureRandom = new SecureRandom();
      byte[]       randomBytes  = new byte[ReceiptSerial.SIZE];

      secureRandom.nextBytes(randomBytes);

      ReceiptSerial             receiptSerial = new ReceiptSerial(randomBytes);
      ClientZkReceiptOperations operations    = AppDependencies.getClientZkReceiptOperations();

      requestContext = operations.createReceiptCredentialRequestContext(secureRandom, receiptSerial);
    } else {
      Log.d(TAG, "Reusing request context from previous run", true);
    }

    Log.d(TAG, "Submitting credential to server", true);
    ServiceResponse<ReceiptCredentialResponse> response = AppDependencies.getDonationsService()
                                                                         .submitBoostReceiptCredentialRequestSync(paymentIntentId, requestContext.getRequest(), donationProcessor);

    if (response.getApplicationError().isPresent()) {
      handleApplicationError(context, response, donationErrorSource);
    } else if (response.getResult().isPresent()) {
      ReceiptCredential receiptCredential = getReceiptCredential(response.getResult().get());

      if (!isCredentialValid(receiptCredential)) {
        DonationError.routeBackgroundError(context, DonationError.badgeCredentialVerificationFailure(donationErrorSource));
        setPendingOneTimeDonationGenericRedemptionError(-1);
        throw new IOException("Could not validate receipt credential");
      }

      Log.d(TAG, "Validated credential. Handing off to next job.", true);
      ReceiptCredentialPresentation receiptCredentialPresentation = getReceiptCredentialPresentation(receiptCredential);
      setOutputData(new JsonJobData.Builder().putBlobAsString(DonationReceiptRedemptionJob.INPUT_RECEIPT_CREDENTIAL_PRESENTATION,
                                                              receiptCredentialPresentation.serialize())
                                             .putBlobAsString(DonationReceiptRedemptionJob.INPUT_TERMINAL_DONATION, terminalDonation.encode())
                                             .serialize());

      if (donationErrorSource == DonationErrorSource.GIFT) {
        SignalStore.inAppPayments().setPendingOneTimeDonation(null);
      }
    } else {
      Log.w(TAG, "Encountered a retryable exception: " + response.getStatus(), response.getExecutionError().orElse(null), true);
      throw new RetryableException();
    }
  }

  /**
   * Sets the pending one-time donation error according to the status code.
   */
  private void setPendingOneTimeDonationGenericRedemptionError(int statusCode) {
    DonationErrorValue donationErrorValue = new DonationErrorValue.Builder()
        .type(statusCode == 402
              ? DonationErrorValue.Type.PAYMENT
              : DonationErrorValue.Type.REDEMPTION)
        .code(Integer.toString(statusCode))
        .build();

    SignalStore.inAppPayments().setPendingOneTimeDonationError(
       donationErrorValue
    );

    terminalDonation = terminalDonation.newBuilder()
                                       .error(donationErrorValue)
                                       .build();
  }

  /**
   * Sets the pending one-time donation error according to the given charge failure.
   */
  private void setPendingOneTimeDonationChargeFailureError(@NonNull ActiveSubscription.ChargeFailure chargeFailure) {
    final DonationErrorValue.Type type;
    final String                  code;

    if (donationProcessor == DonationProcessor.PAYPAL) {
      code = chargeFailure.getCode();
      type = DonationErrorValue.Type.PROCESSOR_CODE;
    } else {
      StripeDeclineCode declineCode = StripeDeclineCode.Companion.getFromCode(chargeFailure.getOutcomeNetworkReason());
      StripeFailureCode failureCode = StripeFailureCode.Companion.getFromCode(chargeFailure.getCode());

      if (failureCode.isKnown()) {
        code = failureCode.toString();
        type = DonationErrorValue.Type.FAILURE_CODE;
      } else if (declineCode.isKnown()) {
        code = declineCode.toString();
        type = DonationErrorValue.Type.DECLINE_CODE;
      } else {
        code = chargeFailure.getCode();
        type = DonationErrorValue.Type.PROCESSOR_CODE;
      }
    }

    DonationErrorValue donationErrorValue = new DonationErrorValue.Builder()
        .type(type)
        .code(code)
        .build();

    SignalStore.inAppPayments().setPendingOneTimeDonationError(
        donationErrorValue
    );

    terminalDonation = terminalDonation.newBuilder()
                                       .error(donationErrorValue)
                                       .build();
  }

  private void handleApplicationError(Context context, ServiceResponse<ReceiptCredentialResponse> response, @NonNull DonationErrorSource donationErrorSource) throws Exception {
    Throwable applicationException = response.getApplicationError().get();
    switch (response.getStatus()) {
      case 204:
        Log.w(TAG, "User payment not be completed yet.", applicationException, true);
        throw new RetryableException();
      case 400:
        Log.w(TAG, "Receipt credential request failed to validate.", applicationException, true);
        DonationError.routeBackgroundError(context, DonationError.genericBadgeRedemptionFailure(donationErrorSource));
        setPendingOneTimeDonationGenericRedemptionError(response.getStatus());
        throw new Exception(applicationException);
      case 402:
        Log.w(TAG, "User payment failed.", applicationException, true);
        DonationError.routeBackgroundError(context, DonationError.genericPaymentFailure(donationErrorSource), terminalDonation.isLongRunningPaymentMethod);

        if (applicationException instanceof InAppPaymentReceiptCredentialError) {
          setPendingOneTimeDonationChargeFailureError(((InAppPaymentReceiptCredentialError) applicationException).getChargeFailure());
        } else {
          setPendingOneTimeDonationGenericRedemptionError(response.getStatus());
        }

        throw new Exception(applicationException);
      case 409:
        Log.w(TAG, "Receipt already redeemed with a different request credential.", response.getApplicationError().get(), true);
        DonationError.routeBackgroundError(context, DonationError.genericBadgeRedemptionFailure(donationErrorSource));
        setPendingOneTimeDonationGenericRedemptionError(response.getStatus());
        throw new Exception(applicationException);
      default:
        Log.w(TAG, "Encountered a server failure: " + response.getStatus(), applicationException, true);
        throw new RetryableException();
    }
  }

  private ReceiptCredentialPresentation getReceiptCredentialPresentation(@NonNull ReceiptCredential receiptCredential) throws RetryableException {
    ClientZkReceiptOperations operations = AppDependencies.getClientZkReceiptOperations();

    try {
      return operations.createReceiptCredentialPresentation(receiptCredential);
    } catch (VerificationFailedException e) {
      Log.w(TAG, "getReceiptCredentialPresentation: encountered a verification failure in zk", e, true);
      requestContext = null;
      throw new RetryableException();
    }
  }

  private ReceiptCredential getReceiptCredential(@NonNull ReceiptCredentialResponse response) throws RetryableException {
    ClientZkReceiptOperations operations = AppDependencies.getClientZkReceiptOperations();

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
    public @NonNull BoostReceiptRequestResponseJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      String              paymentIntentId      = data.getString(DATA_PAYMENT_INTENT_ID);
      DonationErrorSource donationErrorSource  = DonationErrorSource.deserialize(data.getStringOrDefault(DATA_ERROR_SOURCE, DonationErrorSource.ONE_TIME.serialize()));
      long                badgeLevel           = data.getLongOrDefault(DATA_BADGE_LEVEL, Long.parseLong(SubscriptionLevels.BOOST_LEVEL));
      String              rawDonationProcessor = data.getStringOrDefault(DATA_DONATION_PROCESSOR, DonationProcessor.STRIPE.getCode());
      DonationProcessor   donationProcessor    = DonationProcessor.fromCode(rawDonationProcessor);
      long                uiSessionKey         = data.getLongOrDefault(DATA_UI_SESSION_KEY, -1L);
      byte[]              rawTerminalDonation  = data.getStringAsBlob(DATA_TERMINAL_DONATION);

      TerminalDonationQueue.TerminalDonation terminalDonation = null;
      if (rawTerminalDonation != null) {
        try {
          terminalDonation = TerminalDonationQueue.TerminalDonation.ADAPTER.decode(rawTerminalDonation);
        } catch (IOException e) {
          Log.e(TAG, "Failed to parse terminal donation. Generating a default.");
        }
      }

      if (terminalDonation == null) {
        terminalDonation = new TerminalDonationQueue.TerminalDonation(
            -1,
            false,
            null,
            ByteString.EMPTY
        );
      }

      try {
        if (data.hasString(DATA_REQUEST_BYTES)) {
          byte[]                          blob           = data.getStringAsBlob(DATA_REQUEST_BYTES);
          ReceiptCredentialRequestContext requestContext = new ReceiptCredentialRequestContext(blob);

          return new BoostReceiptRequestResponseJob(parameters, requestContext, paymentIntentId, donationErrorSource, badgeLevel, donationProcessor, uiSessionKey, terminalDonation);
        } else {
          return new BoostReceiptRequestResponseJob(parameters, null, paymentIntentId, donationErrorSource, badgeLevel, donationProcessor, uiSessionKey, terminalDonation);
        }
      } catch (InvalidInputException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
