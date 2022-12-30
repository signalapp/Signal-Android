package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable.ReportSpamData;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Report 1 to {@link #MAX_MESSAGE_COUNT} message guids received prior to {@link #timestamp} in {@link #threadId} to the server as spam.
 */
public class ReportSpamJob extends BaseJob {

  public static final  String KEY = "ReportSpamJob";
  private static final String TAG = Log.tag(ReportSpamJob.class);

  private static final String KEY_THREAD_ID     = "thread_id";
  private static final String KEY_TIMESTAMP     = "timestamp";
  private static final int    MAX_MESSAGE_COUNT = 3;

  private final long threadId;
  private final long timestamp;

  public ReportSpamJob(long threadId, long timestamp) {
    this(new Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                                 .setMaxAttempts(5)
                                 .setQueue("ReportSpamJob")
                                 .build(),
         threadId,
         timestamp);
  }

  private ReportSpamJob(@NonNull Parameters parameters, long threadId, long timestamp) {
    super(parameters);
    this.threadId  = threadId;
    this.timestamp = timestamp;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_THREAD_ID, threadId)
                             .putLong(KEY_TIMESTAMP, timestamp)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    if (!SignalStore.account().isRegistered()) {
      return;
    }

    int                         count                       = 0;
    List<ReportSpamData>        reportSpamData              = SignalDatabase.messages().getReportSpamMessageServerData(threadId, timestamp, MAX_MESSAGE_COUNT);
    SignalServiceAccountManager signalServiceAccountManager = ApplicationDependencies.getSignalServiceAccountManager();
    for (ReportSpamData data : reportSpamData) {
      Optional<ServiceId> serviceId = Recipient.resolved(data.getRecipientId()).getServiceId();

      if (serviceId.isPresent() && !serviceId.get().isUnknown()) {
        signalServiceAccountManager.reportSpam(serviceId.get(), data.getServerGuid());
        count++;
      } else {
        Log.w(TAG, "Unable to report spam without an ACI for " + data.getRecipientId());
      }
    }
    Log.i(TAG, "Reported " + count + " out of " + reportSpamData.size() + " messages in thread " + threadId + " as spam");
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof ServerRejectedException) {
      return false;
    } else if (exception instanceof NonSuccessfulResponseCodeException) {
      return ((NonSuccessfulResponseCodeException) exception).is5xx();
    }

    return exception instanceof IOException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Canceling report spam for thread " + threadId);
  }

  public static final class Factory implements Job.Factory<ReportSpamJob> {
    @Override
    public @NonNull ReportSpamJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new ReportSpamJob(parameters, data.getLong(KEY_THREAD_ID), data.getLong(KEY_TIMESTAMP));
    }
  }
}
