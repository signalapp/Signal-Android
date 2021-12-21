package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.subscription.Subscriber;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.internal.EmptyResponse;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Job that, once there is a valid local subscriber id, should be run every 3 days
 * to ensure that a user's subscription does not lapse.
 */
public class SubscriptionKeepAliveJob extends BaseJob {

  public static final String KEY = "SubscriptionKeepAliveJob";

  private static final String TAG         = Log.tag(SubscriptionKeepAliveJob.class);
  private static final long   JOB_TIMEOUT = TimeUnit.DAYS.toMillis(3);

  public static void launchSubscriberIdKeepAliveJobIfNecessary() {
    long nextLaunchTime = SignalStore.donationsValues().getLastKeepAliveLaunchTime() + TimeUnit.DAYS.toMillis(3);
    long now            = System.currentTimeMillis();

    if (nextLaunchTime <= now) {
      ApplicationDependencies.getJobManager().add(new SubscriptionKeepAliveJob());
      SignalStore.donationsValues().setLastKeepAliveLaunchTime(now);
    }
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
  public @NonNull Data serialize() {
    return Data.EMPTY;
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
    Subscriber subscriber = SignalStore.donationsValues().getSubscriber();
    if (subscriber == null) {
      return;
    }

    ServiceResponse<EmptyResponse> response = ApplicationDependencies.getDonationsService()
                                                                     .putSubscription(subscriber.getSubscriberId())
                                                                     .blockingGet();

    verifyResponse(response);
    Log.i(TAG, "Successful call to PUT subscription ID", true);

    ServiceResponse<ActiveSubscription> activeSubscriptionResponse = ApplicationDependencies.getDonationsService()
                                                                                            .getSubscription(subscriber.getSubscriberId())
                                                                                            .blockingGet();

    verifyResponse(activeSubscriptionResponse);
    Log.i(TAG, "Successful call to GET active subscription", true);

    ActiveSubscription activeSubscription = activeSubscriptionResponse.getResult().get();
    if (activeSubscription.getActiveSubscription() == null || !activeSubscription.getActiveSubscription().isActive()) {
      Log.i(TAG, "User does not have an active subscription. Exiting.", true);
      return;
    }

    if (activeSubscription.getActiveSubscription().getEndOfCurrentPeriod() > SignalStore.donationsValues().getLastEndOfPeriod()) {
      Log.i(TAG,
            String.format(Locale.US,
                          "Last end of period change. Requesting receipt refresh. (old: %d to new: %d)",
                          SignalStore.donationsValues().getLastEndOfPeriod(),
                          activeSubscription.getActiveSubscription().getEndOfCurrentPeriod()),
            true);

      SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain(true).enqueue();
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
    public @NonNull SubscriptionKeepAliveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new SubscriptionKeepAliveJob(parameters);
    }
  }
}
