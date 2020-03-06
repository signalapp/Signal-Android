package org.thoughtcrime.securesms.gcm;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.IncomingMessageProcessor;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.jobs.MarkerJob;
import org.thoughtcrime.securesms.jobs.PushDecryptMessageJob;
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob;
import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retrieves messages over the REST endpoint.
 */
public class RestStrategy implements MessageRetriever.Strategy {

  private static final String TAG = Log.tag(RestStrategy.class);

  private static final long SOCKET_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

  @WorkerThread
  @Override
  public boolean run() {
    long startTime = System.currentTimeMillis();

    try (IncomingMessageProcessor.Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
      SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
      AtomicInteger                jobCount = new AtomicInteger(0);

      receiver.setSoTimeoutMillis(SOCKET_TIMEOUT);

      receiver.retrieveMessages(envelope -> {
        Log.i(TAG, "Retrieved an envelope." + timeSuffix(startTime));
        String jobId = processor.processEnvelope(envelope);

        if (jobId != null) {
          jobCount.incrementAndGet();
        }
        Log.i(TAG, "Successfully processed an envelope." + timeSuffix(startTime));
      });

      Log.d(TAG, jobCount.get() + " PushDecryptMessageJob(s) were enqueued.");

      long timeRemainingMs = blockUntilQueueDrained(PushDecryptMessageJob.QUEUE, TimeUnit.SECONDS.toMillis(10));

      if (timeRemainingMs > 0) {
        blockUntilQueueDrained(PushProcessMessageJob.QUEUE, timeRemainingMs);
      }

      return true;
    } catch (IOException e) {
      Log.w(TAG, "Failed to retrieve messages. Resetting the SignalServiceMessageReceiver.", e);
      ApplicationDependencies.resetSignalServiceMessageReceiver();
      return false;
    }
  }

  private static long blockUntilQueueDrained(@NonNull String queue, long timeoutMs) {
    final JobManager jobManager = ApplicationDependencies.getJobManager();
    final MarkerJob  markerJob  = new MarkerJob(queue);

    jobManager.add(markerJob);

    long           startTime = System.currentTimeMillis();
    CountDownLatch latch     = new CountDownLatch(1);

    jobManager.addListener(markerJob.getId(), new JobTracker.JobListener() {
      @Override
      public void onStateChanged(@NonNull Job job, @NonNull JobTracker.JobState jobState) {
        if (jobState.isComplete()) {
          jobManager.removeListener(this);
          latch.countDown();
        }
      }
    });

    try {
      if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        Log.w(TAG, "Timed out waiting for " + queue + " job(s) to finish!");
        return 0;
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }

    long endTime  = System.currentTimeMillis();
    long duration = endTime - startTime;

    Log.d(TAG, "Waited " + duration + " ms for the " + queue + " job(s) to finish.");
    return timeoutMs - duration;
  }

  private static String timeSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }

  @Override
  public @NonNull String toString() {
    return RestStrategy.class.getSimpleName();
  }
}
