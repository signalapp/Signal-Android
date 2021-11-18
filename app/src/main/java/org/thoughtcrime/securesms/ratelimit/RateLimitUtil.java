package org.thoughtcrime.securesms.ratelimit;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushMediaSendJob;
import org.thoughtcrime.securesms.jobs.PushTextSendJob;

import java.util.Set;

public final class RateLimitUtil {

  private static final String TAG = Log.tag(RateLimitUtil.class);

  private RateLimitUtil() {}

  /**
   * Forces a retry of all rate limited messages by editing jobs that are in the queue.
   */
  @WorkerThread
  public static void retryAllRateLimitedMessages(@NonNull Context context) {
    Set<Long> sms = SignalDatabase.sms().getAllRateLimitedMessageIds();
    Set<Long> mms = SignalDatabase.mms().getAllRateLimitedMessageIds();

    if (sms.isEmpty() && mms.isEmpty()) {
      return;
    }

    Log.i(TAG, "Retrying " + sms.size() + " sms records and " + mms.size() + " mms records.");

    SignalDatabase.sms().clearRateLimitStatus(sms);
    SignalDatabase.mms().clearRateLimitStatus(mms);

    ApplicationDependencies.getJobManager().update((job, serializer) -> {
      Data data = serializer.deserialize(job.getSerializedData());

      if (job.getFactoryKey().equals(PushTextSendJob.KEY) && sms.contains(PushTextSendJob.getMessageId(data))) {
        return job.withNextRunAttemptTime(System.currentTimeMillis());
      } else if (job.getFactoryKey().equals(PushMediaSendJob.KEY) && mms.contains(PushMediaSendJob.getMessageId(data))) {
        return job.withNextRunAttemptTime(System.currentTimeMillis());
      } else if (job.getFactoryKey().equals(PushGroupSendJob.KEY) && mms.contains(PushGroupSendJob.getMessageId(data))) {
        return job.withNextRunAttemptTime(System.currentTimeMillis());
      } else {
        return job;
      }
    });
  }
}
