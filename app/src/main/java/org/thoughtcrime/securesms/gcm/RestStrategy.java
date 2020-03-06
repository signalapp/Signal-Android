package org.thoughtcrime.securesms.gcm;

import androidx.annotation.AnyThread;
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
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
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
    long                    startTime     = System.currentTimeMillis();
    JobManager              jobManager    = ApplicationDependencies.getJobManager();
    QueueFindingJobListener queueListener = new QueueFindingJobListener();

    try (IncomingMessageProcessor.Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
      int jobCount = enqueuePushDecryptJobs(processor, startTime);

      if (jobCount == 0) {
        Log.d(TAG, "No PushDecryptMessageJobs were enqueued.");
        return true;
      } else {
        Log.d(TAG, jobCount + " PushDecryptMessageJob(s) were enqueued.");
      }

      jobManager.addListener(job -> job.getParameters().getQueue() != null && job.getParameters().getQueue().startsWith(PushProcessMessageJob.QUEUE_PREFIX), queueListener);

      long        timeRemainingMs = blockUntilQueueDrained(PushDecryptMessageJob.QUEUE, TimeUnit.SECONDS.toMillis(10));
      Set<String> processQueues   = queueListener.getQueues();

      Log.d(TAG, "Discovered " + processQueues.size() + " queue(s): " + processQueues);

      if (timeRemainingMs > 0) {
        Iterator<String> iter = processQueues.iterator();

        while (iter.hasNext() && timeRemainingMs > 0) {
          timeRemainingMs = blockUntilQueueDrained(iter.next(), timeRemainingMs);
        }

        if (timeRemainingMs <= 0) {
          Log.w(TAG, "Ran out of time while waiting for queues to drain.");
        }
      } else {
        Log.w(TAG, "Ran out of time before we could even wait on individual queues!");
      }

      return true;
    } catch (IOException e) {
      Log.w(TAG, "Failed to retrieve messages. Resetting the SignalServiceMessageReceiver.", e);
      ApplicationDependencies.resetSignalServiceMessageReceiver();
      return false;
    } finally {
      jobManager.removeListener(queueListener);
    }
  }

  private static int enqueuePushDecryptJobs(IncomingMessageProcessor.Processor processor, long startTime)
      throws IOException
  {
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

    return jobCount.get();
  }


  private static long blockUntilQueueDrained(@NonNull String queue, long timeoutMs) {
    long             startTime  = System.currentTimeMillis();
    final JobManager jobManager = ApplicationDependencies.getJobManager();
    final MarkerJob  markerJob  = new MarkerJob(queue);

    Optional<JobTracker.JobState> jobState = jobManager.runSynchronously(markerJob, timeoutMs);

    if (!jobState.isPresent()) {
      Log.w(TAG, "Timed out waiting for " + queue + " job(s) to finish!");
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

  private static class QueueFindingJobListener implements JobTracker.JobListener {
    private final Set<String> queues = new HashSet<>();

    @Override
    @AnyThread
    public void onStateChanged(@NonNull Job job, @NonNull JobTracker.JobState jobState) {
      synchronized (queues) {
        queues.add(job.getParameters().getQueue());
      }
    }

    @NonNull Set<String> getQueues() {
      synchronized (queues) {
        return new HashSet<>(queues);
      }
    }
  }
}
