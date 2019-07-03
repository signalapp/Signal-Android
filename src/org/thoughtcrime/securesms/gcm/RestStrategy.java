package org.thoughtcrime.securesms.gcm;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.IncomingMessageProcessor;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Retrieves messages over the REST endpoint.
 */
public class RestStrategy implements MessageRetriever.Strategy {

  private static final String TAG = Log.tag(RestStrategy.class);

  private static final long SOCKET_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

  private final Context context;

  public RestStrategy(@NonNull Context context) {
    this.context = context;
  }

  @WorkerThread
  @Override
  public boolean run() {
    long startTime = System.currentTimeMillis();

    try (IncomingMessageProcessor.Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
      SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
      receiver.setSoTimeoutMillis(SOCKET_TIMEOUT);

      receiver.retrieveMessages(envelope -> {
        Log.i(TAG, "Retrieved an envelope." + timeSuffix(startTime));
        processor.processEnvelope(envelope);
        Log.i(TAG, "Successfully processed an envelope." + timeSuffix(startTime));
      });

      return true;
    } catch (IOException e) {
      Log.w(TAG, "Failed to retrieve messages.", e);
      return false;
    }
  }

  private static String timeSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }

  @Override
  public @NonNull String toString() {
    return RestStrategy.class.getSimpleName();
  }
}
