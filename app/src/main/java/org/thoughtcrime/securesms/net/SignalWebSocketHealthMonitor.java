package org.thoughtcrime.securesms.net;

import android.app.Application;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.HealthMonitor;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Monitors the health of the identified and unidentified WebSockets. If either one appears to be
 * unhealthy, will trigger restarting both.
 * <p>
 * The monitor is also responsible for sending heartbeats/keep-alive messages to prevent
 * timeouts.
 */
public final class SignalWebSocketHealthMonitor implements HealthMonitor {

  private static final String TAG = Log.tag(SignalWebSocketHealthMonitor.class);

  private static final long KEEP_ALIVE_SEND_CADENCE              = TimeUnit.SECONDS.toMillis(WebSocketConnection.KEEPALIVE_TIMEOUT_SECONDS);
  private static final long MAX_TIME_SINCE_SUCCESSFUL_KEEP_ALIVE = KEEP_ALIVE_SEND_CADENCE * 3;

  private final Executor executor = Executors.newSingleThreadExecutor();

  private final Application     context;
  private       SignalWebSocket signalWebSocket;
  private final SleepTimer      sleepTimer;

  private KeepAliveSender keepAliveSender;

  private final HealthState identified   = new HealthState();
  private final HealthState unidentified = new HealthState();

  public SignalWebSocketHealthMonitor(@NonNull Application context, @NonNull SleepTimer sleepTimer) {
    this.context    = context;
    this.sleepTimer = sleepTimer;
  }

  public void monitor(@NonNull SignalWebSocket signalWebSocket) {
    executor.execute(() -> {
      Preconditions.checkNotNull(signalWebSocket);
      Preconditions.checkArgument(this.signalWebSocket == null, "monitor can only be called once");

      this.signalWebSocket = signalWebSocket;

      //noinspection ResultOfMethodCallIgnored
      signalWebSocket.getWebSocketState()
                     .subscribeOn(Schedulers.computation())
                     .observeOn(Schedulers.computation())
                     .distinctUntilChanged()
                     .subscribe(s -> onStateChange(s, identified, true));

      //noinspection ResultOfMethodCallIgnored
      signalWebSocket.getUnidentifiedWebSocketState()
                     .subscribeOn(Schedulers.computation())
                     .observeOn(Schedulers.computation())
                     .distinctUntilChanged()
                     .subscribe(s -> onStateChange(s, unidentified, false));
    });
  }

  private void onStateChange(WebSocketConnectionState connectionState, HealthState healthState, boolean isIdentified) {
    executor.execute(() -> {
      switch (connectionState) {
        case CONNECTED:
          if (isIdentified) {
            TextSecurePreferences.setUnauthorizedReceived(context, false);
            break;
          }
        case AUTHENTICATION_FAILED:
          if (isIdentified) {
            TextSecurePreferences.setUnauthorizedReceived(context, true);
            break;
          }
        case FAILED:
          break;
      }

      healthState.needsKeepAlive = connectionState == WebSocketConnectionState.CONNECTED;

      if (keepAliveSender == null && isKeepAliveNecessary()) {
        keepAliveSender = new KeepAliveSender();
        keepAliveSender.start();
      } else if (keepAliveSender != null && !isKeepAliveNecessary()) {
        keepAliveSender.shutdown();
        keepAliveSender = null;
      }
    });
  }

  @Override
  public void onKeepAliveResponse(long sentTimestamp, boolean isIdentifiedWebSocket) {
    executor.execute(() -> {
      if (isIdentifiedWebSocket) {
        identified.lastKeepAliveReceived = System.currentTimeMillis();
      } else {
        unidentified.lastKeepAliveReceived = System.currentTimeMillis();
      }
    });
  }

  @Override
  public void onMessageError(int status, boolean isIdentifiedWebSocket) {
    executor.execute(() -> {
      if (status == 409) {
        HealthState healthState = (isIdentifiedWebSocket ? identified : unidentified);
        if (healthState.mismatchErrorTracker.addSample(System.currentTimeMillis())) {
          Log.w(TAG, "Received too many mismatch device errors, forcing new websockets.");
          signalWebSocket.forceNewWebSockets();
        }
      }
    });
  }

  private boolean isKeepAliveNecessary() {
    return identified.needsKeepAlive || unidentified.needsKeepAlive;
  }

  private static class HealthState {
    private final HttpErrorTracker mismatchErrorTracker = new HttpErrorTracker(5, TimeUnit.MINUTES.toMillis(1));

    private volatile boolean needsKeepAlive;
    private volatile long    lastKeepAliveReceived;
  }

  /**
   * Sends periodic heartbeats/keep-alives over both WebSockets to prevent connection timeouts. If
   * either WebSocket fails 3 times to get a return heartbeat both are forced to be recreated.
   */
  private class KeepAliveSender extends Thread {

    private volatile boolean shouldKeepRunning = true;

    public void run() {
      identified.lastKeepAliveReceived   = System.currentTimeMillis();
      unidentified.lastKeepAliveReceived = System.currentTimeMillis();

      while (shouldKeepRunning && isKeepAliveNecessary()) {
        try {
          sleepTimer.sleep(KEEP_ALIVE_SEND_CADENCE);

          if (shouldKeepRunning && isKeepAliveNecessary()) {
            long keepAliveRequiredSinceTime = System.currentTimeMillis() - MAX_TIME_SINCE_SUCCESSFUL_KEEP_ALIVE;

            if (identified.lastKeepAliveReceived < keepAliveRequiredSinceTime || unidentified.lastKeepAliveReceived < keepAliveRequiredSinceTime) {
              Log.w(TAG, "Missed keep alives, identified last: " + identified.lastKeepAliveReceived +
                         " unidentified last: " + unidentified.lastKeepAliveReceived +
                         " needed by: " + keepAliveRequiredSinceTime);
              signalWebSocket.forceNewWebSockets();
            } else {
              signalWebSocket.sendKeepAlive();
            }
          }
        } catch (Throwable e) {
          Log.w(TAG, e);
        }
      }
    }

    public void shutdown() {
      shouldKeepRunning = false;
    }
  }
}
