package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository;
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobStatus;
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobWatcher;
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.internal.EmptyResponse;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import okio.ByteString;

/**
 * Job that, once there is a valid local subscriber id, should be run every 3 days
 * to ensure that a user's subscription does not lapse.
 *
 * @deprecated Replaced with InAppPaymentKeepAliveJob
 */
@Deprecated()
public class SubscriptionKeepAliveJob extends BaseJob {

  public static final String KEY = "SubscriptionKeepAliveJob";

  private static final String TAG         = Log.tag(SubscriptionKeepAliveJob.class);

  private SubscriptionKeepAliveJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
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
    synchronized (InAppPaymentSubscriberRecord.Type.DONATION) {
      doRun();
    }
  }

  private void doRun() throws Exception {
    InAppPaymentSubscriberRecord subscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION);
    if (subscriber == null) {
      return;
    }

    ServiceResponse<EmptyResponse> response = AppDependencies.getDonationsService()
                                                             .putSubscription(subscriber.getSubscriberId());

    verifyResponse(response);
    Log.i(TAG, "Successful call to PUT subscription ID", true);

    ServiceResponse<ActiveSubscription> activeSubscriptionResponse = AppDependencies.getDonationsService()
                                                                                    .getSubscription(subscriber.getSubscriberId());

    verifyResponse(activeSubscriptionResponse);
    Log.i(TAG, "Successful call to GET active subscription", true);

    ActiveSubscription activeSubscription = activeSubscriptionResponse.getResult().get();
    if (activeSubscription.getActiveSubscription() == null) {
      Log.i(TAG, "User does not have a subscription. Exiting.", true);
      return;
    }

    DonationRedemptionJobStatus status = DonationRedemptionJobWatcher.getSubscriptionRedemptionJobStatus();
    if (status != DonationRedemptionJobStatus.None.INSTANCE && status != DonationRedemptionJobStatus.FailedSubscription.INSTANCE) {
      Log.i(TAG, "Already trying to redeem donation, current status: " + status.getClass().getSimpleName(), true);
      return;
    }

    final long endOfCurrentPeriod = activeSubscription.getActiveSubscription().getEndOfCurrentPeriod();
    if (endOfCurrentPeriod > SignalStore.inAppPayments().getLastEndOfPeriod()) {
      Log.i(TAG,
            String.format(Locale.US,
                          "Last end of period change. Requesting receipt refresh. (old: %d to new: %d)",
                          SignalStore.inAppPayments().getLastEndOfPeriod(),
                          activeSubscription.getActiveSubscription().getEndOfCurrentPeriod()),
            true);

      SignalStore.inAppPayments().setLastEndOfPeriod(endOfCurrentPeriod);
      SignalStore.inAppPayments().clearSubscriptionRequestCredential();
      SignalStore.inAppPayments().clearSubscriptionReceiptCredential();
      MultiDeviceSubscriptionSyncRequestJob.enqueue();
    }

    TerminalDonationQueue.TerminalDonation terminalDonation = new TerminalDonationQueue.TerminalDonation(
        activeSubscription.getActiveSubscription().getLevel(),
        Objects.equals(activeSubscription.getActiveSubscription().getPaymentMethod(), ActiveSubscription.PAYMENT_METHOD_SEPA_DEBIT),
        null,
        ByteString.EMPTY
    );

    if (endOfCurrentPeriod > SignalStore.inAppPayments().getSubscriptionEndOfPeriodConversionStarted()) {
      Log.i(TAG, "Subscription end of period is after the conversion end of period. Storing it, generating a credential, and enqueuing the continuation job chain.", true);
      SignalStore.inAppPayments().setSubscriptionEndOfPeriodConversionStarted(endOfCurrentPeriod);
      SignalStore.inAppPayments().refreshSubscriptionRequestCredential();

      SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain(true, -1L, terminalDonation).enqueue();
    } else if (endOfCurrentPeriod > SignalStore.inAppPayments().getSubscriptionEndOfPeriodRedemptionStarted()) {
      if (SignalStore.inAppPayments().getSubscriptionRequestCredential() == null) {
        Log.i(TAG, "We have not started a redemption, but do not have a request credential. Possible that the subscription changed.", true);
        return;
      }

      Log.i(TAG, "We have a request credential and have not yet turned it into a redeemable token.", true);
      SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain(true, -1L, terminalDonation).enqueue();
    } else if (endOfCurrentPeriod > SignalStore.inAppPayments().getSubscriptionEndOfPeriodRedeemed()) {
      if (SignalStore.inAppPayments().getSubscriptionReceiptCredential() == null) {
        Log.i(TAG, "We have successfully started redemption but have no stored token. Possible that the subscription changed.", true);
        return;
      }

      Log.i(TAG, "We have a receipt credential and have not yet redeemed it.", true);
      DonationReceiptRedemptionJob.createJobChainForKeepAlive().enqueue();
    } else {
      Log.i(TAG, "Subscription is active, and end of current period (remote) is after the latest checked end of period (local). Nothing to do.");
    }
  }

  private <T> void verifyResponse(@NonNull ServiceResponse<T> response) throws Exception {
    if (response.getExecutionError().isPresent()) {
      Log.w(TAG, "Failed with an execution error. Scheduling retry.", response.getExecutionError().get(), true);
      throw new RetryableException();
    } else if (response.getApplicationError().isPresent()) {
      switch (response.getStatus()) {
        case 403:
        case 404:
          Log.w(TAG, "Invalid or malformed subscriber id. Status: " + response.getStatus(), response.getApplicationError().get(), true);
          throw new IOException();
        default:
          Log.w(TAG, "An unknown server error occurred: " + response.getStatus(), response.getApplicationError().get(), true);
          throw new RetryableException();
      }
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryableException;
  }

  private static class RetryableException extends Exception {
  }

  public static class Factory implements Job.Factory<SubscriptionKeepAliveJob> {
    @Override
    public @NonNull SubscriptionKeepAliveJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new SubscriptionKeepAliveJob(parameters);
    }
  }
}
