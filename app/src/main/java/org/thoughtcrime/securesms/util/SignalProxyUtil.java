package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.conscrypt.ConscryptSignal;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class SignalProxyUtil {

  private static final String TAG = Log.tag(SignalProxyUtil.class);

  private static final String PROXY_LINK_HOST = "signal.tube";

  private static final Pattern PROXY_LINK_PATTERN = Pattern.compile("^(https|sgnl)://" + PROXY_LINK_HOST + "/#([^:]+).*$");
  private static final Pattern HOST_PATTERN       = Pattern.compile("^([^:]+).*$");

  private SignalProxyUtil() {}

  public static void startListeningToWebsocket() {
    if (SignalStore.proxy().isProxyEnabled() && AppDependencies.getSignalWebSocket().getWebSocketState().firstOrError().blockingGet().isFailure()) {
      Log.w(TAG, "Proxy is in a failed state. Restarting.");
      AppDependencies.resetNetwork();
    }

    AppDependencies.getIncomingMessageObserver();
  }

  /**
   * Handles all things related to enabling a proxy, including saving it and resetting the relevant
   * network connections.
   */
  public static void enableProxy(@NonNull SignalProxy proxy) {
    SignalStore.proxy().enableProxy(proxy);
    ConscryptSignal.setUseEngineSocketByDefault(true);
    AppDependencies.resetNetwork();
    startListeningToWebsocket();
  }

  /**
   * Handles all things related to disabling a proxy, including saving the change and resetting the
   * relevant network connections.
   */
  public static void disableProxy() {
    SignalStore.proxy().disableProxy();
    ConscryptSignal.setUseEngineSocketByDefault(false);
    AppDependencies.resetNetwork();
    startListeningToWebsocket();
  }

  public static void disableAndClearProxy(){
    disableProxy();
    SignalStore.proxy().setProxy(null);
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

    if (SignalStore.account().getE164() == null) {
      Log.i(TAG, "User is unregistered! Doing simple check.");
      return testWebsocketConnectionUnregistered(timeout);
    }

    return AppDependencies.getSignalWebSocket()
                          .getWebSocketState()
                          .subscribeOn(Schedulers.trampoline())
                          .observeOn(Schedulers.trampoline())
                          .timeout(timeout, TimeUnit.MILLISECONDS)
                          .skipWhile(state -> state != WebSocketConnectionState.CONNECTED && !state.isFailure())
                          .firstOrError()
                          .flatMap(state -> Single.just(state == WebSocketConnectionState.CONNECTED))
                          .onErrorReturn(t -> false)
                          .blockingGet();
  }

  /**
   * If this is a valid proxy deep link, this will return the embedded host. If not, it will return
   * null.
   */
  public static @Nullable String parseHostFromProxyDeepLink(@Nullable String proxyLink) {
    if (proxyLink == null) {
      return null;
    }

    Matcher matcher = PROXY_LINK_PATTERN.matcher(proxyLink);

    if (matcher.matches()) {
      return matcher.group(2);
    } else {
      return null;
    }
  }

  /**
   * Takes in an address that could be in various formats, and converts it to the format we should
   * be storing and connecting to.
   */
  public static @NonNull String convertUserEnteredAddressToHost(@NonNull String host) {
    String parsedHost = SignalProxyUtil.parseHostFromProxyDeepLink(host);
    if (parsedHost != null) {
      return parsedHost;
    }

    Matcher matcher = HOST_PATTERN.matcher(host);

    if (matcher.matches()) {
      String result = matcher.group(1);
      return result != null ? result : "";
    } else {
      return host;
    }
  }

  public static @NonNull String generateProxyUrl(@NonNull String link) {
    String host   = link;
    String parsed = parseHostFromProxyDeepLink(link);

    if (parsed != null) {
      host = parsed;
    }

    Matcher matcher = HOST_PATTERN.matcher(host);

    if (matcher.matches()) {
      host = matcher.group(1);
    }

    return "https://" + PROXY_LINK_HOST + "/#" + host;
  }

  private static boolean testWebsocketConnectionUnregistered(long timeout) {
    CountDownLatch              latch          = new CountDownLatch(1);
    AtomicBoolean               success        = new AtomicBoolean(false);
    SignalServiceAccountManager accountManager = AccountManagerFactory.getInstance().createUnauthenticated(AppDependencies.getApplication(), "", SignalServiceAddress.DEFAULT_DEVICE_ID, "");

    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        accountManager.checkNetworkConnection();
        success.set(true);
        latch.countDown();
      } catch (IOException e) {
        latch.countDown();
      }
    });

    try {
      latch.await(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted!", e);
    }

    return success.get();
  }
}
