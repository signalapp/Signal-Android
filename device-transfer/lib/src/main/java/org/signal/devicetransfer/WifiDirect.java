package org.signal.devicetransfer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Build;
import android.os.HandlerThread;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.WifiDirectUnavailableException.Reason;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provide the ability to spin up a WiFi Direct network, advertise a network service,
 * discover a network service, and then connect two devices.
 */
public final class WifiDirect {

  private static final String TAG = Log.tag(WifiDirect.class);

  private static final IntentFilter intentFilter = new IntentFilter() {{
    addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
  }};

  private static final String  EXTRA_INFO_PLACEHOLDER    = "%%EXTRA_INFO%%";
  private static final String  SERVICE_INSTANCE_TEMPLATE = "_devicetransfer" + EXTRA_INFO_PLACEHOLDER + "._signal.org";
  private static final Pattern SERVICE_INSTANCE_PATTERN  = Pattern.compile("_devicetransfer(\\._(.+))?\\._signal\\.org");
  private static final String  SERVICE_REG_TYPE          = "_presence._tcp";

  private static final long SAFE_FOR_LONG_AWAIT_TIMEOUT     = TimeUnit.SECONDS.toMillis(5);
  private static final long NOT_SAFE_FOR_LONG_AWAIT_TIMEOUT = 50;

  private final Context                      context;
  private       WifiDirectConnectionListener connectionListener;
  private       WifiDirectCallbacks          wifiDirectCallbacks;
  private       WifiP2pManager               manager;
  private       WifiP2pManager.Channel       channel;
  private       WifiP2pDnsSdServiceRequest   serviceRequest;
  private final HandlerThread                wifiDirectCallbacksHandler;

  /**
   * Determine the ability to use WiFi Direct by checking if the device supports WiFi Direct
   * and the appropriate permissions have been granted.
   */
  public static @NonNull AvailableStatus getAvailability(@NonNull Context context) {
    if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
      Log.i(TAG, "Feature not available");
      return AvailableStatus.FEATURE_NOT_AVAILABLE;
    }

    WifiManager wifiManager = ContextCompat.getSystemService(context, WifiManager.class);
    if (wifiManager == null) {
      Log.i(TAG, "WifiManager not available");
      return AvailableStatus.WIFI_MANAGER_NOT_AVAILABLE;
    }

    if (Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "Fine location permission required");
      return AvailableStatus.FINE_LOCATION_PERMISSION_NOT_GRANTED;
    }

    return Build.VERSION.SDK_INT <= 23 || wifiManager.isP2pSupported() ? AvailableStatus.AVAILABLE
                                                                       : AvailableStatus.WIFI_DIRECT_NOT_AVAILABLE;
  }

  WifiDirect(@NonNull Context context) {
    this.context                    = context.getApplicationContext();
    this.wifiDirectCallbacksHandler = SignalExecutors.getAndStartHandlerThread("wifi-direct-cb", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD);
  }

  /**
   * Initialize {@link WifiP2pManager} and {@link WifiP2pManager.Channel} needed to interact
   * with the Android WiFi Direct APIs. This should have a matching call to {@link #shutdown()} to
   * release the various resources used to establish and maintain a WiFi Direct network.
   */
  synchronized void initialize(@NonNull WifiDirectConnectionListener connectionListener) throws WifiDirectUnavailableException {
    if (isInitialized()) {
      Log.w(TAG, "Already initialized, do not need to initialize twice");
      return;
    }

    this.connectionListener = connectionListener;

    manager = ContextCompat.getSystemService(context, WifiP2pManager.class);
    if (manager == null) {
      Log.i(TAG, "Unable to get WifiP2pManager");
      shutdown();
      throw new WifiDirectUnavailableException(Reason.WIFI_P2P_MANAGER);
    }

    wifiDirectCallbacks = new WifiDirectCallbacks(connectionListener);
    channel             = manager.initialize(context, wifiDirectCallbacksHandler.getLooper(), wifiDirectCallbacks);
    if (channel == null) {
      Log.i(TAG, "Unable to initialize channel");
      shutdown();
      throw new WifiDirectUnavailableException(Reason.CHANNEL_INITIALIZATION);
    }

    context.registerReceiver(wifiDirectCallbacks, intentFilter);
  }

  /**
   * Clears and releases WiFi Direct resources that may have been created or in use. Also
   * shuts down the WiFi Direct related {@link HandlerThread}.
   * <p>
   * <i>Note: After this call, the instance is no longer usable and an entirely new one will need to
   * be created.</i>
   */
  synchronized void shutdown() {
    Log.d(TAG, "Shutting down");

    connectionListener = null;

    if (manager != null) {
      retrySync(manager::clearServiceRequests, "clear service requests", SAFE_FOR_LONG_AWAIT_TIMEOUT);
      retrySync(manager::stopPeerDiscovery, "stop peer discovery", SAFE_FOR_LONG_AWAIT_TIMEOUT);
      retrySync(manager::clearLocalServices, "clear local services", SAFE_FOR_LONG_AWAIT_TIMEOUT);
      if (Build.VERSION.SDK_INT < 27) {
        retrySync(manager::removeGroup, "remove group", SAFE_FOR_LONG_AWAIT_TIMEOUT);
        channel = null;
      }
      manager = null;
    }

    if (channel != null && Build.VERSION.SDK_INT >= 27) {
      channel.close();
      channel = null;
    }

    if (wifiDirectCallbacks != null) {
      wifiDirectCallbacks.clearConnectionListener();
      context.unregisterReceiver(wifiDirectCallbacks);
      wifiDirectCallbacks = null;
    }

    wifiDirectCallbacksHandler.quit();
    wifiDirectCallbacksHandler.interrupt();
  }

  /**
   * Start advertising a transfer service that other devices can search for and decide
   * to connect to. Call on an appropriate thread as this method synchronously calls WiFi Direct
   * methods.
   *
   * @param extraInfo Extra info to include in the service instance name (e.g., server port)
   */
  @WorkerThread
  @SuppressLint("MissingPermission")
  synchronized void startDiscoveryService(@NonNull String extraInfo) throws WifiDirectUnavailableException {
    ensureInitialized();

    WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(buildServiceInstanceName(extraInfo), SERVICE_REG_TYPE, Collections.emptyMap());

    SyncActionListener addLocalServiceListener = new SyncActionListener("add local service", SAFE_FOR_LONG_AWAIT_TIMEOUT);
    manager.addLocalService(channel, serviceInfo, addLocalServiceListener);

    SyncActionListener discoverPeersListener = new SyncActionListener("discover peers", SAFE_FOR_LONG_AWAIT_TIMEOUT);
    manager.discoverPeers(channel, discoverPeersListener);

    if (!addLocalServiceListener.successful() || !discoverPeersListener.successful()) {
      throw new WifiDirectUnavailableException(Reason.SERVICE_START);
    }
  }

  /**
   * Stop all peer discovery and advertising services.
   */
  synchronized void stopDiscoveryService() throws WifiDirectUnavailableException {
    ensureInitialized();

    retryAsync(manager::stopPeerDiscovery, "stop peer discovery");
    retryAsync(manager::clearLocalServices, "clear local services");
  }

  /**
   * Start searching for a transfer service being advertised by another device. Call on an
   * appropriate thread as this method synchronously calls WiFi Direct methods.
   */
  @WorkerThread
  @SuppressLint("MissingPermission")
  synchronized void discoverService() throws WifiDirectUnavailableException {
    ensureInitialized();

    if (serviceRequest != null) {
      Log.w(TAG, "Discover service already called and active.");
      return;
    }

    WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record, device) -> {};

    WifiP2pManager.DnsSdServiceResponseListener serviceListener = (instanceName, registrationType, sourceDevice) -> {
      String extraInfo = isInstanceNameMatching(instanceName);
      if (extraInfo != null) {
        Log.d(TAG, "Service found!");
        WifiDirectConnectionListener listener = connectionListener;
        if (listener != null) {
          listener.onServiceDiscovered(sourceDevice, extraInfo);
        }
      } else {
        Log.d(TAG, "Found unusable service, ignoring.");
      }
    };

    manager.setDnsSdResponseListeners(channel, serviceListener, txtListener);

    serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

    SyncActionListener addServiceListener = new SyncActionListener("add service request", SAFE_FOR_LONG_AWAIT_TIMEOUT);
    manager.addServiceRequest(channel, serviceRequest, addServiceListener);

    SyncActionListener startDiscovery = new SyncActionListener("discover services", SAFE_FOR_LONG_AWAIT_TIMEOUT);
    manager.discoverServices(channel, startDiscovery);

    if (!addServiceListener.successful() || !startDiscovery.successful()) {
      manager.removeServiceRequest(channel, serviceRequest, null);
      serviceRequest = null;
      throw new WifiDirectUnavailableException(Reason.SERVICE_DISCOVERY_START);
    }
  }

  /**
   * Stop searching for transfer services.
   */
  synchronized void stopServiceDiscovery() throws WifiDirectUnavailableException {
    ensureInitialized();

    retryAsync(manager::clearServiceRequests, "clear service requests");
  }

  /**
   * Establish a WiFi Direct network by connecting to the given device address (MAC). An
   * address can be found by using {@link #discoverService()}.
   *
   * @param deviceAddress Device MAC address to establish a connection with
   */
  @SuppressLint("MissingPermission")
  synchronized void connect(@NonNull String deviceAddress) throws WifiDirectUnavailableException {
    ensureInitialized();

    WifiP2pConfig config = new WifiP2pConfig();
    config.deviceAddress    = deviceAddress;
    config.wps.setup        = WpsInfo.PBC;
    config.groupOwnerIntent = 0;

    if (serviceRequest != null) {
      manager.removeServiceRequest(channel, serviceRequest, LoggingActionListener.message("Remote service request"));
      serviceRequest = null;
    }

    SyncActionListener listener = new SyncActionListener("service connect", SAFE_FOR_LONG_AWAIT_TIMEOUT);
    manager.connect(channel, config, listener);

    if (listener.successful()) {
      Log.i(TAG, "Successfully connected to service.");
    } else {
      throw new WifiDirectUnavailableException(Reason.SERVICE_CONNECT_FAILURE);
    }
  }

  public synchronized void requestNetworkInfo() throws WifiDirectUnavailableException {
    ensureInitialized();

    manager.requestConnectionInfo(channel, info -> {
      Log.i(TAG, "Connection information available. group_formed: " + info.groupFormed + " group_owner: " + info.isGroupOwner);
      WifiDirectConnectionListener listener = connectionListener;
      if (listener != null) {
        listener.onNetworkConnected(info);
      }
    });
  }

  private synchronized void retrySync(@NonNull ManagerRetry retryFunction, @NonNull String message, long awaitTimeout) {
    int tries = 3;

    while ((tries--) > 0) {
      if (isNotInitialized()) {
        return;
      }

      SyncActionListener listener = new SyncActionListener(message, awaitTimeout);
      retryFunction.call(channel, listener);
      if (listener.successful() || listener.failureReason == SyncActionListener.FAILURE_TIMEOUT) {
        return;
      }
      ThreadUtil.sleep(TimeUnit.SECONDS.toMillis(1));
    }
  }

  private void retryAsync(@NonNull ManagerRetry retryFunction, @NonNull String message) {
    SignalExecutors.BOUNDED.execute(() -> retrySync(retryFunction, message, WifiDirect.NOT_SAFE_FOR_LONG_AWAIT_TIMEOUT));
  }

  private synchronized boolean isInitialized() {
    return manager != null && channel != null;
  }

  private synchronized boolean isNotInitialized() {
    return manager == null || channel == null;
  }

  private void ensureInitialized() throws WifiDirectUnavailableException {
    if (isNotInitialized()) {
      Log.w(TAG, "WiFi Direct has not been initialized.");
      throw new WifiDirectUnavailableException(Reason.SERVICE_NOT_INITIALIZED);
    }
  }

  @VisibleForTesting
  static @NonNull String buildServiceInstanceName(@Nullable String extraInfo) {
    if (TextUtils.isEmpty(extraInfo)) {
      return SERVICE_INSTANCE_TEMPLATE.replace(EXTRA_INFO_PLACEHOLDER, "");
    }
    return SERVICE_INSTANCE_TEMPLATE.replace(EXTRA_INFO_PLACEHOLDER, "._" + extraInfo);
  }

  @VisibleForTesting
  static @Nullable String isInstanceNameMatching(@NonNull String serviceInstanceName) {
    Matcher matcher = SERVICE_INSTANCE_PATTERN.matcher(serviceInstanceName);
    if (matcher.matches()) {
      String extraInfo = matcher.group(2);
      return TextUtils.isEmpty(extraInfo) ? "" : extraInfo;
    }
    return null;
  }

  private interface ManagerRetry {
    void call(@NonNull WifiP2pManager.Channel a, @NonNull WifiP2pManager.ActionListener b);
  }

  private static class WifiDirectCallbacks extends BroadcastReceiver implements WifiP2pManager.ChannelListener {
    private WifiDirectConnectionListener connectionListener;

    public WifiDirectCallbacks(@NonNull WifiDirectConnectionListener connectionListener) {
      this.connectionListener = connectionListener;
    }

    public void clearConnectionListener() {
      connectionListener = null;
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
      String action = intent.getAction();
      if (action != null) {
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
          WifiDirectConnectionListener listener    = connectionListener;
          NetworkInfo                  networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
          if (networkInfo == null) {
            Log.w(TAG, "WiFi P2P broadcast connection changed action with null network info.");
            return;
          }

          if (listener != null) {
            listener.onConnectionChanged(networkInfo);
          }
        }
      }
    }

    @Override
    public void onChannelDisconnected() {
      WifiDirectConnectionListener listener = connectionListener;
      if (listener != null) {
        listener.onNetworkFailure();
      }
    }
  }

  /**
   * Provide a synchronous way to talking to Android's WiFi Direct code.
   */
  private static class SyncActionListener extends LoggingActionListener {

    private static final int FAILURE_TIMEOUT = -2;

    private final    CountDownLatch sync;
    private final    long           awaitTimeout;
    private volatile int            failureReason = -1;

    public SyncActionListener(@NonNull String message, long awaitTimeout) {
      super(message);
      this.awaitTimeout = awaitTimeout;
      this.sync         = new CountDownLatch(1);
    }

    @Override
    public void onSuccess() {
      super.onSuccess();
      sync.countDown();
    }

    @Override
    public void onFailure(int reason) {
      super.onFailure(reason);
      failureReason = reason;
      sync.countDown();
    }

    public boolean successful() {
      try {
        boolean completed = sync.await(awaitTimeout, TimeUnit.MILLISECONDS);
        if (!completed) {
          Log.i(TAG, "SyncListener [" + message + "] timed out after " + awaitTimeout + "ms");
          failureReason = FAILURE_TIMEOUT;
          return false;
        }
      } catch (InterruptedException ie) {
        Log.i(TAG, "SyncListener [" + message + "] interrupted");
      }
      return failureReason < 0;
    }
  }

  private static class LoggingActionListener implements WifiP2pManager.ActionListener {

    protected final String message;

    public static @NonNull LoggingActionListener message(@Nullable String message) {
      return new LoggingActionListener(message);
    }

    public LoggingActionListener(@Nullable String message) {
      this.message = message;
    }

    @Override
    public void onSuccess() {
      Log.i(TAG, message + " success");
    }

    @Override
    public void onFailure(int reason) {
      Log.w(TAG, message + " failure_reason: " + reason);
    }
  }

  public enum AvailableStatus {
    FEATURE_NOT_AVAILABLE,
    WIFI_MANAGER_NOT_AVAILABLE,
    FINE_LOCATION_PERMISSION_NOT_GRANTED,
    WIFI_DIRECT_NOT_AVAILABLE,
    AVAILABLE
  }

  public interface WifiDirectConnectionListener {

    void onServiceDiscovered(@NonNull WifiP2pDevice serviceDevice, @NonNull String extraInfo);

    void onNetworkConnected(@NonNull WifiP2pInfo info);

    void onNetworkFailure();

    void onConnectionChanged(@NonNull NetworkInfo networkInfo);
  }
}
