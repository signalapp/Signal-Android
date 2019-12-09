package org.thoughtcrime.securesms.gcm;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.Observer;

import org.thoughtcrime.securesms.IncomingMessageProcessor;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
      SignalServiceMessageReceiver receiver  = ApplicationDependencies.getSignalServiceMessageReceiver();
      AtomicReference<String>      lastJobId = new AtomicReference<>(null);
      AtomicInteger                jobCount  = new AtomicInteger(0);

      receiver.setSoTimeoutMillis(SOCKET_TIMEOUT);

      receiver.retrieveMessages(envelope -> {
        Log.i(TAG, "Retrieved an envelope." + timeSuffix(startTime));
        String jobId = processor.processEnvelope(envelope);

        if (jobId != null) {
          lastJobId.set(jobId);
          jobCount.incrementAndGet();
        }
        Log.i(TAG, "Successfully processed an envelope." + timeSuffix(startTime));
      });

      Log.d(TAG, jobCount.get() + " PushDecryptJob(s) were enqueued.");

      if (lastJobId.get() != null) {
        blockUntilJobIsFinished(lastJobId.get());
      }

      return true;
    } catch (IOException e) {
      Log.w(TAG, "Failed to retrieve messages. Resetting the SignalServiceMessageReceiver.", e);
      ApplicationDependencies.resetSignalServiceMessageReceiver();
      return false;
    }
  }
  private static void blockUntilJobIsFinished(@NonNull String jobId) {
    long           startTime = System.currentTimeMillis();
    CountDownLatch latch     = new CountDownLatch(1);

    ApplicationDependencies.getJobManager().addListener(jobId, new JobTracker.JobListener() {
      @Override
      public void onStateChanged(@NonNull JobTracker.JobState jobState) {
        if (jobState.isComplete()) {
          ApplicationDependencies.getJobManager().removeListener(this);
          latch.countDown();
        }
      }
    });

    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        Log.w(TAG, "Timed out waiting for PushDecryptJob(s) to finish!");
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }

    Log.d(TAG, "Waited " + (System.currentTimeMillis() - startTime) + " ms for the PushDecryptJob(s) to finish.");
  }

  private static String timeSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }

  @Override
  public @NonNull String toString() {
    return RestStrategy.class.getSimpleName();
  }
}
