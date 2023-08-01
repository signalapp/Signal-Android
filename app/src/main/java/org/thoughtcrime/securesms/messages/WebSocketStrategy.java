package org.thoughtcrime.securesms.messages;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.Stopwatch;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.jobs.MarkerJob;
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Retrieves messages over the websocket.
 */
public class WebSocketStrategy extends MessageRetrievalStrategy {

  private static final String TAG = Log.tag(WebSocketStrategy.class);

  private static final String KEEP_ALIVE_TOKEN = "WebsocketStrategy";
  private static final long   QUEUE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

  private final long websocketDrainTimeoutMs;
  public WebSocketStrategy() {
    this(TimeUnit.MINUTES.toMillis(1));
  }

  public WebSocketStrategy(long websocketDrainTimeoutMs) {
    this.websocketDrainTimeoutMs = websocketDrainTimeoutMs;
  }

  @WorkerThread
  @Override
  public boolean execute() {
    Stopwatch               stopwatch = new Stopwatch("websocket-strategy");
    IncomingMessageObserver observer  = ApplicationDependencies.getIncomingMessageObserver();

    observer.registerKeepAliveToken(KEEP_ALIVE_TOKEN);
    try {
      JobManager              jobManager    = ApplicationDependencies.getJobManager();
      QueueFindingJobListener queueListener = new QueueFindingJobListener();

      jobManager.addListener(job -> job.getParameters().getQueue() != null && job.getParameters().getQueue().startsWith(PushProcessMessageJob.QUEUE_PREFIX), queueListener);

      if (!blockUntilWebsocketDrained(observer, websocketDrainTimeoutMs)) {
        return false;
      }

      stopwatch.split("decryptions-drained");

      Set<String> processQueues = queueListener.getQueues();
      Log.d(TAG, "Discovered " + processQueues.size() + " queue(s): " + processQueues);

      for (String queue : processQueues) {
        if (!blockUntilJobQueueDrained(queue, QUEUE_TIMEOUT)) {
          return false;
        }
      }

      stopwatch.split("process-drained");
      stopwatch.stop(TAG);

      return true;
    } finally {
      ApplicationDependencies.getIncomingMessageObserver().removeKeepAliveToken(KEEP_ALIVE_TOKEN);
    }
  }

  private static boolean blockUntilWebsocketDrained(IncomingMessageObserver observer, long timeoutMs) {
    CountDownLatch latch = new CountDownLatch(1);

    observer.addDecryptionDrainedListener(new Runnable() {
      @Override public void run() {
        latch.countDown();
        observer.removeDecryptionDrainedListener(this);
      }
    });

    try {
      if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        return true;
      } else {
        Log.w(TAG, "Hit timeout while waiting for decryptions to drain!");
        return false;
      }
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted!", e);
      return false;
    }
  }

  private static boolean blockUntilJobQueueDrained(@NonNull String queue, long timeoutMs) {
    long             startTime  = System.currentTimeMillis();
    final JobManager jobManager = ApplicationDependencies.getJobManager();
    final MarkerJob  markerJob  = new MarkerJob(queue);

    Optional<JobTracker.JobState> jobState = jobManager.runSynchronously(markerJob, timeoutMs);

    if (!jobState.isPresent()) {
      Log.w(TAG, "Timed out waiting for " + queue + " job(s) to finish!");
      return false;
    }

    long endTime  = System.currentTimeMillis();
    long duration = endTime - startTime;

    Log.d(TAG, "Waited " + duration + " ms for the " + queue + " job(s) to finish.");
    return true;
  }

  @Override
  public @NonNull String toString() {
    return Log.tag(WebSocketStrategy.class);
  }
}
