package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobStatus;
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobWatcher;
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.subscription.Subscriber;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.internal.EmptyResponse;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okio.ByteString;

/**
 * Job that, once there is a valid local subscriber id, should be run every 3 days
 * to ensure that a user's subscription does not lapse.
 */
public class SubscriptionKeepAliveJob extends BaseJob {

  public static final String KEY = "SubscriptionKeepAliveJob";

  private static final String TAG         = Log.tag(SubscriptionKeepAliveJob.class);
  private static final long   JOB_TIMEOUT = TimeUnit.DAYS.toMillis(3);

  public static void enqueueAndTrackTimeIfNecessary() {
    long nextLaunchTime = SignalStore.donationsValues().getLastKeepAliveLaunchTime() + TimeUnit.DAYS.toMillis(3);
    long now            = System.currentTimeMillis();

    if (nextLaunchTime <= now) {
      enqueueAndTrackTime(now);
    }
  }

  public static void enqueueAndTrackTime(long now) {
    ApplicationDependencies.getJobManager().add(new SubscriptionKeepAliveJob());
    SignalStore.donationsValues().setLastKeepAliveLaunchTime(now);
  }

  private SubscriptionKeepAliveJob() {
    this(new Parameters.Builder()
                       .setQueue(KEY)
                       .addConstraint(NetworkConstraint.KEY)
                       .setMaxInstancesForQueue(1)
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setLifespan(JOB_TIMEOUT)
                       .build());
  }

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
    synchronized (SubscriptionReceiptRequestResponseJob.MUTEX) {
      doRun();
    }
  }

  private void doRun() throws Exception {
    Subscriber subscriber = SignalStore.donationsValues().getSubscriber();
    if (subscriber == null) {
      return;
    }

    ServiceResponse<EmptyResponse> response = ApplicationDependencies.getDonationsService()
                                                                     .putSubscription(subscriber.getSubscriberId());

    verifyResponse(response);
    Log.i(TAG, "Successful call to PUT subscription ID", true);

    ServiceResponse<ActiveSubscription> activeSubscriptionResponse = ApplicationDependencies.getDonationsService()
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
    if (endOfCurrentPeriod > SignalStore.donationsValues().getLastEndOfPeriod()) {
      Log.i(TAG,
            String.format(Locale.US,
                          "Last end of period change. Requesting receipt refresh. (old: %d to new: %d)",
                          SignalStore.donationsValues().getLastEndOfPeriod(),
                          activeSubscription.getActiveSubscription().getEndOfCurrentPeriod()),
            true);

      SignalStore.donationsValues().setLastEndOfPeriod(endOfCurrentPeriod);
      SignalStore.donationsValues().clearSubscriptionRequestCredential();
      SignalStore.donationsValues().clearSubscriptionReceiptCredential();
      MultiDeviceSubscriptionSyncRequestJob.enqueue();
    }

    TerminalDonationQueue.TerminalDonation terminalDonation = new TerminalDonationQueue.TerminalDonation(
        activeSubscription.getActiveSubscription().getLevel(),
        Objects.equals(activeSubscription.getActiveSubscription().getPaymentMethod(), ActiveSubscription.PAYMENT_METHOD_SEPA_DEBIT),
        null,
        ByteString.EMPTY
    );

    if (endOfCurrentPeriod > SignalStore.donationsValues().getSubscriptionEndOfPeriodConversionStarted()) {
      Log.i(TAG, "Subscription end of period is after the conversion end of period. Storing it, generating a credential, and enqueuing the continuation job chain.", true);
      SignalStore.donationsValues().setSubscriptionEndOfPeriodConversionStarted(endOfCurrentPeriod);
      SignalStore.donationsValues().refreshSubscriptionRequestCredential();

      SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain(true, -1L, terminalDonation).enqueue();
    } else if (endOfCurrentPeriod > SignalStore.donationsValues().getSubscriptionEndOfPeriodRedemptionStarted()) {
      if (SignalStore.donationsValues().getSubscriptionRequestCredential() == null) {
        Log.i(TAG, "We have not started a redemption, but do not have a request credential. Possible that the subscription changed.", true);
        return;
      }

      Log.i(TAG, "We have a request credential and have not yet turned it into a redeemable token.", true);
      SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain(true, -1L, terminalDonation).enqueue();
    } else if (endOfCurrentPeriod > SignalStore.donationsValues().getSubscriptionEndOfPeriodRedeemed()) {
      if (SignalStore.donationsValues().getSubscriptionReceiptCredential() == null) {
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
