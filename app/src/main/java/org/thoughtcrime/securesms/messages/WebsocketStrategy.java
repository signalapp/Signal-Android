package org.thoughtcrime.securesms.messages;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeoutException;

class WebsocketStrategy extends MessageRetrievalStrategy {

  private static final String TAG = Log.tag(WebsocketStrategy.class);

  private final SignalWebSocket signalWebSocket;
  private final JobManager      jobManager;

  public WebsocketStrategy() {
    this.signalWebSocket = ApplicationDependencies.getSignalWebSocket();
    this.jobManager      = ApplicationDependencies.getJobManager();
  }

  @Override
  public boolean execute(long timeout) {
    long startTime = System.currentTimeMillis();

    try {
      Set<String>      processJobQueues = drainWebsocket(timeout, startTime);
      Iterator<String> queueIterator    = processJobQueues.iterator();
      long             timeRemaining    = Math.max(0, timeout - (System.currentTimeMillis() - startTime));

      while (!isCanceled() && queueIterator.hasNext() && timeRemaining > 0) {
        String queue = queueIterator.next();

        blockUntilQueueDrained(TAG, queue, timeRemaining);

        timeRemaining = Math.max(0, timeout - (System.currentTimeMillis() - startTime));
      }

      return true;
    } catch (IOException e) {
      Log.w(TAG, "Encountered an exception while draining the websocket.", e);
      return false;
    }
  }

  private @NonNull Set<String> drainWebsocket(long timeout, long startTime) throws IOException {
    QueueFindingJobListener  queueListener = new QueueFindingJobListener();

    jobManager.addListener(job -> job.getParameters().getQueue() != null && job.getParameters().getQueue().startsWith(PushProcessMessageJob.QUEUE_PREFIX), queueListener);

    try {
      signalWebSocket.connect();
      while (shouldContinue()) {
        try {
          Optional<SignalServiceEnvelope> result = signalWebSocket.readOrEmpty(timeout, envelope -> {
            Log.i(TAG, "Retrieved envelope! " + envelope.getTimestamp() + timeSuffix(startTime));
            try (IncomingMessageProcessor.Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
              processor.processEnvelope(envelope);
            }
          });

          if (!result.isPresent()) {
            Log.i(TAG, "Hit an empty response. Finished." + timeSuffix(startTime));
            break;
          }
        } catch (TimeoutException e) {
          Log.w(TAG, "Websocket timeout." + timeSuffix(startTime));
        }
      }
    } finally {
      signalWebSocket.disconnect();
      jobManager.removeListener(queueListener);
    }

    return queueListener.getQueues();
  }


  private boolean shouldContinue() {
    return !isCanceled();
  }
}
