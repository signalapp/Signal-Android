package org.thoughtcrime.securesms.messages;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.jobs.MarkerJob;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementations are responsible for fetching and processing a batch of messages.
 */
public abstract class MessageRetrievalStrategy {

  private volatile boolean canceled;

  /**
   * Fetches and processes any pending messages. This method should block until the messages are
   * actually stored and processed -- not just retrieved.
   *
   * @param timeout Hint for how long this will run. The strategy will also be canceled after the
   *                timeout ends, but having the timeout available may be useful for setting things
   *                like socket timeouts.
   *
   * @return True if everything was successful up until cancelation, false otherwise.
   */
  @WorkerThread
  abstract boolean execute(long timeout);

  /**
   * Marks the strategy as canceled. It is the responsibility of the implementation of
   * {@link #execute(long)} to check {@link #isCanceled()} to know if execution should stop.
   */
  void cancel() {
    this.canceled = true;
  }

  protected boolean isCanceled() {
    return canceled;
  }

  protected static void blockUntilQueueDrained(@NonNull String tag, @NonNull String queue, long timeoutMs) {
    long             startTime  = System.currentTimeMillis();
    final JobManager jobManager = ApplicationDependencies.getJobManager();
    final MarkerJob  markerJob  = new MarkerJob(queue);

    Optional<JobTracker.JobState> jobState = jobManager.runSynchronously(markerJob, timeoutMs);

    if (!jobState.isPresent()) {
      Log.w(tag, "Timed out waiting for " + queue + " job(s) to finish!");
    }

    long endTime  = System.currentTimeMillis();
    long duration = endTime - startTime;

    Log.d(tag, "Waited " + duration + " ms for the " + queue + " job(s) to finish.");
  }

  protected static String timeSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }

  protected static class QueueFindingJobListener implements JobTracker.JobListener {
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
