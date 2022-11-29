package org.thoughtcrime.securesms.messages;

import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil;
import org.thoughtcrime.securesms.jobs.PushDecryptDrainedJob;
import org.thoughtcrime.securesms.jobs.UnableToStartException;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.messages.IncomingMessageProcessor.Processor;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The application-level manager of our websocket connection.
 * <p>
 * This class is responsible for opening/closing the websocket based on the app's state and observing new inbound messages received on the websocket.
 */
public class IncomingMessageObserver {

  private static final String TAG = Log.tag(IncomingMessageObserver.class);

  public static final  int  FOREGROUND_ID           = 313399;
  private static final long REQUEST_TIMEOUT_MINUTES = 1;
  private static final long OLD_REQUEST_WINDOW_MS   = TimeUnit.MINUTES.toMillis(5);

  private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(0);

  private final Application                context;
  private final SignalServiceNetworkAccess networkAccess;
  private final List<Runnable>             decryptionDrainedListeners;
  private final BroadcastReceiver          connectionReceiver;
  private final Map<String, Long>          keepAliveTokens;

  private boolean appVisible;

  private volatile boolean networkDrained;
  private volatile boolean decryptionDrained;
  private volatile boolean terminated;

  public IncomingMessageObserver(@NonNull Application context) {
    if (INSTANCE_COUNT.incrementAndGet() != 1) {
      throw new AssertionError("Multiple observers!");
    }

    this.context                    = context;
    this.networkAccess              = ApplicationDependencies.getSignalServiceNetworkAccess();
    this.decryptionDrainedListeners = new CopyOnWriteArrayList<>();
    this.keepAliveTokens            = new HashMap<>();

    new MessageRetrievalThread().start();

    if (!SignalStore.account().isFcmEnabled() || SignalStore.internalValues().isWebsocketModeForced()) {
      try {
        ForegroundServiceUtil.startWhenCapable(context, new Intent(context, ForegroundService.class));
      } catch (UnableToStartException e) {
        Log.w(TAG, "Unable to start foreground service for websocket!", e);
      }
    }

    ApplicationDependencies.getAppForegroundObserver().addListener(new AppForegroundObserver.Listener() {
      @Override
      public void onForeground() {
        onAppForegrounded();
      }

      @Override
      public void onBackground() {
        onAppBackgrounded();
      }
    });

    connectionReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        synchronized (IncomingMessageObserver.this) {
          if (!NetworkConstraint.isMet(context)) {
            Log.w(TAG, "Lost network connection. Shutting down our websocket connections and resetting the drained state.");
            networkDrained    = false;
            decryptionDrained = false;
            disconnect();
          }
          IncomingMessageObserver.this.notifyAll();
        }
      }
    };

    context.registerReceiver(connectionReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
  }

  public synchronized void notifyRegistrationChanged() {
    notifyAll();
  }

  public synchronized void addDecryptionDrainedListener(@NonNull Runnable listener) {
    decryptionDrainedListeners.add(listener);
    if (decryptionDrained) {
      listener.run();
    }
  }

  public boolean isDecryptionDrained() {
    return decryptionDrained || networkAccess.isCensored();
  }

  public void notifyDecryptionsDrained() {
    List<Runnable> listenersToTrigger = new ArrayList<>(decryptionDrainedListeners.size());

    synchronized (this) {
      if (networkDrained && !decryptionDrained) {
        Log.i(TAG, "Decryptions newly drained.");
        decryptionDrained = true;
        listenersToTrigger.addAll(decryptionDrainedListeners);
      }
    }

    for (Runnable listener : listenersToTrigger) {
      listener.run();
    }
  }

  private synchronized void onAppForegrounded() {
    appVisible = true;
    notifyAll();
  }

  private synchronized void onAppBackgrounded() {
    appVisible = false;
    notifyAll();
  }

  private synchronized boolean isConnectionNecessary() {
    boolean registered     = SignalStore.account().isRegistered();
    boolean fcmEnabled     = SignalStore.account().isFcmEnabled();
    boolean hasNetwork     = NetworkConstraint.isMet(context);
    boolean hasProxy       = SignalStore.proxy().isProxyEnabled();
    boolean forceWebsocket = SignalStore.internalValues().isWebsocketModeForced();
    long    oldRequest     = System.currentTimeMillis() - OLD_REQUEST_WINDOW_MS;

    boolean removedRequests = keepAliveTokens.entrySet().removeIf(e -> e.getValue() < oldRequest);
    if (removedRequests) {
      Log.d(TAG, "Removed old keep web socket open requests.");
    }

    Log.d(TAG, String.format("Network: %s, Foreground: %s, FCM: %s, Stay open requests: [%s], Censored: %s, Registered: %s, Proxy: %s, Force websocket: %s",
                             hasNetwork, appVisible, fcmEnabled, Util.join(keepAliveTokens.entrySet(), ","), networkAccess.isCensored(), registered, hasProxy, forceWebsocket));

    return registered &&
           (appVisible || !fcmEnabled || forceWebsocket || Util.hasItems(keepAliveTokens)) &&
           hasNetwork &&
           !networkAccess.isCensored();
  }

  private synchronized void waitForConnectionNecessary() {
    try {
      while (!isConnectionNecessary()) wait();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public void terminateAsync() {
    INSTANCE_COUNT.decrementAndGet();

    context.unregisterReceiver(connectionReceiver);

    SignalExecutors.BOUNDED.execute(() -> {
      Log.w(TAG, "Beginning termination.");
      terminated = true;
      disconnect();
    });
  }

  private void disconnect() {
    ApplicationDependencies.getSignalWebSocket().disconnect();
  }

  public synchronized void registerKeepAliveToken(String key) {
    keepAliveTokens.put(key, System.currentTimeMillis());
    notifyAll();
  }

  public synchronized void removeKeepAliveToken(String key) {
    keepAliveTokens.remove(key);
    notifyAll();
  }

  private class MessageRetrievalThread extends Thread implements Thread.UncaughtExceptionHandler {

    MessageRetrievalThread() {
      super("MessageRetrievalService");
      Log.i(TAG, "Initializing! (" + this.hashCode() + ")");
      setUncaughtExceptionHandler(this);
    }

    @Override
    public void run() {
      int attempts = 0;

      while (!terminated) {
        Log.i(TAG, "Waiting for websocket state change....");
        if (attempts > 1) {
          long backoff = BackoffUtil.exponentialBackoff(attempts, TimeUnit.SECONDS.toMillis(30));
          Log.w(TAG, "Too many failed connection attempts,  attempts: " + attempts + " backing off: " + backoff);
          ThreadUtil.sleep(backoff);
        }
        waitForConnectionNecessary();

        Log.i(TAG, "Making websocket connection....");
        SignalWebSocket signalWebSocket = ApplicationDependencies.getSignalWebSocket();
        signalWebSocket.connect();

        try {
          while (isConnectionNecessary()) {
            try {
              Log.d(TAG, "Reading message...");
              Optional<SignalServiceEnvelope> result = signalWebSocket.readOrEmpty(TimeUnit.MINUTES.toMillis(REQUEST_TIMEOUT_MINUTES), envelope -> {
                Log.i(TAG, "Retrieved envelope! " + envelope.getTimestamp());
                try (Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
                  processor.processEnvelope(envelope);
                }
              });
              attempts = 0;

              if (!result.isPresent() && !networkDrained) {
                Log.i(TAG, "Network was newly-drained. Enqueuing a job to listen for decryption draining.");
                networkDrained = true;
                ApplicationDependencies.getJobManager().add(new PushDecryptDrainedJob());
              }
            } catch (WebSocketUnavailableException e) {
              Log.i(TAG, "Pipe unexpectedly unavailable, connecting");
              signalWebSocket.connect();
            } catch (TimeoutException e) {
              Log.w(TAG, "Application level read timeout...");
              attempts = 0;
            }
          }
        } catch (Throwable e) {
          attempts++;
          Log.w(TAG, e);
        } finally {
          Log.w(TAG, "Shutting down pipe...");
          disconnect();
        }

        Log.i(TAG, "Looping...");
      }

      Log.w(TAG, "Terminated! (" + this.hashCode() + ")");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      Log.w(TAG, "*** Uncaught exception!");
      Log.w(TAG, e);
    }
  }

  public static class ForegroundService extends Service {

    @Override
    public @Nullable IBinder onBind(Intent intent) {
      return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      super.onStartCommand(intent, flags, startId);

      NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NotificationChannels.BACKGROUND);
      builder.setContentTitle(getApplicationContext().getString(R.string.MessageRetrievalService_signal));
      builder.setContentText(getApplicationContext().getString(R.string.MessageRetrievalService_background_connection_enabled));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setWhen(0);
      builder.setSmallIcon(R.drawable.ic_signal_background_connection);
      startForeground(FOREGROUND_ID, builder.build());

      return Service.START_STICKY;
    }
  }
}
