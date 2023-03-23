package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServiceOutageDetectionJob extends BaseJob {

  public static final String KEY = "ServiceOutageDetectionJob";

  private static final String TAG = Log.tag(ServiceOutageDetectionJob.class);

  private static final String IP_SUCCESS = "127.0.0.1";
  private static final String IP_FAILURE = "127.0.0.2";
  private static final long   CHECK_TIME = 1000 * 60;

  public ServiceOutageDetectionJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("ServiceOutageDetectionJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(5)
                           .setMaxInstancesForFactory(1)
                           .build());
  }

  private ServiceOutageDetectionJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws RetryLaterException {
    Log.i(TAG, "onRun()");

    long timeSinceLastCheck = System.currentTimeMillis() - TextSecurePreferences.getLastOutageCheckTime(context);
    if (timeSinceLastCheck < CHECK_TIME) {
      Log.w(TAG, "Skipping service outage check. Too soon.");
      return;
    }

    try {
      InetAddress address = InetAddress.getByName(BuildConfig.SIGNAL_SERVICE_STATUS_URL);
      Log.i(TAG, "Received outage check address: " + address.getHostAddress());

      if (IP_SUCCESS.equals(address.getHostAddress())) {
        Log.i(TAG, "Service is available.");
        TextSecurePreferences.setServiceOutage(context, false);
      } else if (IP_FAILURE.equals(address.getHostAddress())) {
        Log.w(TAG, "Service is down.");
        TextSecurePreferences.setServiceOutage(context, true);
      } else {
        Log.w(TAG, "Service status check returned an unrecognized IP address. Could be a weird network state. Prompting retry.");
        throw new RetryLaterException(new Exception("Unrecognized service outage IP address."));
      }

      TextSecurePreferences.setLastOutageCheckTime(context, System.currentTimeMillis());
      EventBus.getDefault().post(new ReminderUpdateEvent());
    } catch (UnknownHostException e) {
      throw new RetryLaterException(e);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
    Log.i(TAG, "Service status check could not complete. Assuming success to avoid false positives due to bad network.");
    TextSecurePreferences.setServiceOutage(context, false);
    TextSecurePreferences.setLastOutageCheckTime(context, System.currentTimeMillis());
    EventBus.getDefault().post(new ReminderUpdateEvent());
  }

  public static final class Factory implements Job.Factory<ServiceOutageDetectionJob> {
    @Override
    public @NonNull ServiceOutageDetectionJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ServiceOutageDetectionJob(parameters);
    }
  }
}
