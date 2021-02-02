package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.Observer;

import org.conscrypt.Conscrypt;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.PipeConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SignalProxyUtil {

  private static final String TAG = Log.tag(SignalProxyUtil.class);

  private static final String PROXY_LINK_HOST = "signal.tube";

  private SignalProxyUtil() {}

  public static void startListeningToWebsocket() {
    ApplicationDependencies.getIncomingMessageObserver();
  }

  /**
   * Handles all things related to enabling a proxy, including saving it and resetting the relevant
   * network connections.
   */
  public static void enableProxy(@NonNull SignalProxy proxy) {
    SignalStore.proxy().enableProxy(proxy);
    Conscrypt.setUseEngineSocketByDefault(true);
    ApplicationDependencies.resetNetworkConnectionsAfterProxyChange();
    startListeningToWebsocket();
  }

  /**
   * Handles all things related to disabling a proxy, including saving the change and resetting the
   * relevant network connections.
   */
  public static void disableProxy() {
    SignalStore.proxy().disableProxy();
    Conscrypt.setUseEngineSocketByDefault(false);
    ApplicationDependencies.resetNetworkConnectionsAfterProxyChange();
    startListeningToWebsocket();
  }

  /**
   * A blocking call that will wait until the websocket either successfully connects, or fails.
   * It is assumed that the app state is already configured how you would like it, e.g. you've
   * already configured a proxy if relevant.
   *
   * @return True if the connection is successful within the specified timeout, otherwise false.
   */
  @WorkerThread
  public static boolean testWebsocketConnection(long timeout) {
    startListeningToWebsocket();

    CountDownLatch latch   = new CountDownLatch(1);
    AtomicBoolean  success = new AtomicBoolean(false);

    Observer<PipeConnectivityListener.State> observer = state -> {
      if (state == PipeConnectivityListener.State.CONNECTED) {
        success.set(true);
        latch.countDown();
      } else if (state == PipeConnectivityListener.State.FAILURE) {
        success.set(false);
        latch.countDown();
      }
    };

    Util.runOnMainSync(() -> ApplicationDependencies.getPipeListener().getState().observeForever(observer));

    try {
      latch.await(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted!", e);
    } finally {
      Util.runOnMainSync(() -> ApplicationDependencies.getPipeListener().getState().removeObserver(observer));
    }

    return success.get();
  }

  /**
   * If this is a valid proxy link, this will return the embedded host. If not, it will return
   * null.
   */
  public static @Nullable String parseHostFromProxyLink(@NonNull String proxyLink) {
    try {
      URI uri = new URI(proxyLink);

      if (!"https".equalsIgnoreCase(uri.getScheme())) {
        return null;
      }

      if (!PROXY_LINK_HOST.equalsIgnoreCase(uri.getHost())) {
        return null;
      }

      String path = uri.getPath();

      if (Util.isEmpty(path) || "/".equals(path)) {
        return null;
      }

      if (path.startsWith("/")) {
        return path.substring(1);
      } else {
        return path;
      }
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
