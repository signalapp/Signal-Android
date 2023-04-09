package org.signal.devicetransfer;

import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates the logic to find and establish a WiFi Direct connection with another
 * device and then perform an arbitrary {@link ClientTask} with the TCP socket.
 * <p>
 * The client attempts multiple times to establish the network and deal with connectivity
 * problems. It will also retry the task if an issue occurs while running it.
 * <p>
 * The client is setup to retry indefinitely and will only bail on its own if it's
 * unable to start {@link WifiDirect} or the network client connects and then completes
 * or failed. A call to {@link #stop()} is required to stop client from the "outside."
 * <p>
 * Summary of mitigations:
 * <ul>
 *   <li>Completely tear down and restart WiFi direct if no server is found within the timeout.</li>
 *   <li>Retry connecting to the WiFi Direct network, and after all retries fail it does a complete tear down and restart.</li>
 *   <li>Retry connecting to the server until successful, disconnected from WiFi Direct network, or told to stop.</li>
 * </ul>
 */
final class DeviceTransferClient implements Handler.Callback {

  private static final String TAG = Log.tag(DeviceTransferClient.class);

  private static final int START_CLIENT               = 0;
  private static final int STOP_CLIENT                = 1;
  private static final int START_NETWORK_CLIENT       = 2;
  private static final int NETWORK_DISCONNECTED       = 3;
  private static final int CONNECT_TO_SERVICE         = 4;
  private static final int RESTART_CLIENT             = 5;
  private static final int START_IP_EXCHANGE          = 6;
  private static final int IP_EXCHANGE_SUCCESS        = 7;
  private static final int SET_VERIFIED               = 8;
  private static final int NETWORK_CONNECTION_CHANGED = 9;

  private final Context                     context;
  private       int                         remotePort;
  private       HandlerThread               commandAndControlThread;
  private final Handler                     handler;
  private final ClientTask                  clientTask;
  private final ShutdownCallback            shutdownCallback;
  private       WifiDirect                  wifiDirect;
  private       NetworkClientThread         clientThread;
  private final Runnable                    autoRestart;
  private       IpExchange.IpExchangeThread ipExchangeThread;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private static void update(@NonNull TransferStatus transferStatus) {
    Log.d(TAG, "transferStatus: " + transferStatus.getTransferMode().name());
    EventBus.getDefault().postSticky(transferStatus);
  }

  @AnyThread
  public DeviceTransferClient(@NonNull Context context,
                              @NonNull ClientTask clientTask,
                              @Nullable ShutdownCallback shutdownCallback)
  {
    this.context                 = context;
    this.clientTask              = clientTask;
    this.shutdownCallback        = shutdownCallback;
    this.commandAndControlThread = SignalExecutors.getAndStartHandlerThread("client-cnc", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD);
    this.handler                 = new Handler(commandAndControlThread.getLooper(), this);
    this.autoRestart             = () -> {
      Log.i(TAG, "Restarting WiFi Direct since we haven't found anything yet and it could be us.");
      handler.sendEmptyMessage(RESTART_CLIENT);
    };
  }

  @MainThread
  public void start() {
    if (started.compareAndSet(false, true)) {
      update(TransferStatus.ready());
      handler.sendEmptyMessage(START_CLIENT);
    }
  }

  @MainThread
  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      handler.sendEmptyMessage(STOP_CLIENT);
    }
  }

  @MainThread
  public void setVerified(boolean isVerified) {
    if (!stopped.get()) {
      handler.sendMessage(handler.obtainMessage(SET_VERIFIED, isVerified));
    }
  }

  private void shutdown() {
    stopIpExchange();
    stopNetworkClient();
    stopWifiDirect();

    if (commandAndControlThread != null) {
      Log.i(TAG, "Shutting down command and control");
      commandAndControlThread.quit();
      commandAndControlThread.interrupt();
      commandAndControlThread = null;
    }

    EventBus.getDefault().removeStickyEvent(TransferStatus.class);
  }

  private void internalShutdown() {
    shutdown();
    if (shutdownCallback != null) {
      shutdownCallback.shutdown();
    }
  }

  @Override
  public boolean handleMessage(@NonNull Message message) {
    Log.d(TAG, "Handle message: " + message.what);
    switch (message.what) {
      case START_CLIENT:
        startWifiDirect();
        break;
      case STOP_CLIENT:
        shutdown();
        break;
      case START_NETWORK_CLIENT:
        startNetworkClient((String) message.obj);
        break;
      case NETWORK_DISCONNECTED:
        stopNetworkClient();
        break;
      case CONNECT_TO_SERVICE:
        stopServiceDiscovery();
        connectToService((String) message.obj, message.arg1);
        break;
      case RESTART_CLIENT:
        stopNetworkClient();
        stopWifiDirect();
        startWifiDirect();
        break;
      case START_IP_EXCHANGE:
        startIpExchange((String) message.obj);
        break;
      case IP_EXCHANGE_SUCCESS:
        ipExchangeSuccessful((String) message.obj);
        break;
      case SET_VERIFIED:
        if (clientThread != null) {
          clientThread.setVerified((Boolean) message.obj);
        }
        break;
      case NETWORK_CONNECTION_CHANGED:
        requestNetworkInfo((Boolean) message.obj);
        break;
      case NetworkClientThread.NETWORK_CLIENT_SSL_ESTABLISHED:
        update(TransferStatus.verificationRequired((Integer) message.obj));
        break;
      case NetworkClientThread.NETWORK_CLIENT_CONNECTED:
        update(TransferStatus.serviceConnected());
        break;
      case NetworkClientThread.NETWORK_CLIENT_DISCONNECTED:
        update(TransferStatus.networkConnected());
        break;
      case NetworkClientThread.NETWORK_CLIENT_STOPPED:
        update(TransferStatus.shutdown());
        internalShutdown();
        break;
      default:
        internalShutdown();
        throw new AssertionError("Unknown message: " + message.what);
    }
    return false;
  }

  private void startWifiDirect() {
    if (wifiDirect != null) {
      Log.e(TAG, "Client already started");
      return;
    }

    update(TransferStatus.startingUp());

    try {
      wifiDirect = new WifiDirect(context);
      wifiDirect.initialize(new WifiDirectListener());
      wifiDirect.discoverService();
      Log.i(TAG, "Started service discovery, searching for service...");
      update(TransferStatus.discovery());
      handler.postDelayed(autoRestart, TimeUnit.SECONDS.toMillis(15));
    } catch (WifiDirectUnavailableException e) {
      Log.e(TAG, e);
      internalShutdown();
      if (e.getReason() == WifiDirectUnavailableException.Reason.CHANNEL_INITIALIZATION ||
          e.getReason() == WifiDirectUnavailableException.Reason.WIFI_P2P_MANAGER) {
        update(TransferStatus.unavailable());
      } else {
        update(TransferStatus.failed());
      }
    }
  }

  private void stopServiceDiscovery() {
    if (wifiDirect == null) {
      return;
    }

    try {
      Log.i(TAG, "Stopping service discovery");
      wifiDirect.stopServiceDiscovery();
    } catch (WifiDirectUnavailableException e) {
      internalShutdown();
      update(TransferStatus.failed());
    }
  }

  private void stopWifiDirect() {
    handler.removeCallbacks(autoRestart);

    if (wifiDirect != null) {
      Log.i(TAG, "Shutting down WiFi Direct");
      wifiDirect.shutdown();
      wifiDirect = null;
    }
  }

  private void startNetworkClient(@NonNull String serverHostAddress) {
    if (clientThread != null) {
      Log.i(TAG, "Client already running");
      return;
    }

    Log.i(TAG, "Connection established, spinning up network client.");
    clientThread = new NetworkClientThread(context,
                                           clientTask,
                                           serverHostAddress,
                                           remotePort,
                                           handler);
    clientThread.start();
  }

  private void stopNetworkClient() {
    if (clientThread != null) {
      Log.i(TAG, "Shutting down ClientThread");
      clientThread.shutdown();
      try {
        clientThread.join(TimeUnit.SECONDS.toMillis(1));
      } catch (InterruptedException e) {
        Log.i(TAG, "Client thread took too long to shutdown", e);
      }
      clientThread = null;
    }
  }

  private void connectToService(@NonNull String deviceAddress, int port) {
    if (wifiDirect == null) {
      Log.w(TAG, "WifiDirect is not initialized, we shouldn't be here.");
      return;
    }

    if (clientThread != null) {
      Log.i(TAG, "Client is running we shouldn't be connecting again");
      return;
    }

    handler.removeCallbacks(autoRestart);

    int tries = 5;
    while ((tries--) > 0) {
      try {
        wifiDirect.connect(deviceAddress);
        update(TransferStatus.networkConnected());
        remotePort = port;
        return;
      } catch (WifiDirectUnavailableException e) {
        Log.w(TAG, "Unable to connect, tries: " + tries);
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException ignored) {
          Log.i(TAG, "Interrupted while connecting to service, bail now!");
          return;
        }
      }
    }

    handler.sendMessage(handler.obtainMessage(RESTART_CLIENT));
  }

  private void requestNetworkInfo(boolean isNetworkConnected) {
    if (wifiDirect == null) {
      return;
    }

    if (isNetworkConnected) {
      Log.i(TAG, "Network connected, requesting network info");
      try {
        wifiDirect.requestNetworkInfo();
      } catch (WifiDirectUnavailableException e) {
        Log.e(TAG, e);
        internalShutdown();
        update(TransferStatus.failed());
      }
    } else {
      Log.i(TAG, "Network disconnected");
      handler.sendEmptyMessage(NETWORK_DISCONNECTED);
    }
  }

  private void startIpExchange(@NonNull String groupOwnerHostAddress) {
    ipExchangeThread = IpExchange.getIp(groupOwnerHostAddress, remotePort, handler, IP_EXCHANGE_SUCCESS);
  }

  private void stopIpExchange() {
    if (ipExchangeThread != null) {
      ipExchangeThread.shutdown();
      try {
        ipExchangeThread.join(TimeUnit.SECONDS.toMillis(1));
      } catch (InterruptedException e) {
        Log.i(TAG, "IP Exchange thread took too long to shutdown", e);
      }
      ipExchangeThread = null;
    }
  }

  private void ipExchangeSuccessful(@NonNull String host) {
    stopIpExchange();
    handler.sendMessage(handler.obtainMessage(START_NETWORK_CLIENT, host));
  }

  final class WifiDirectListener implements WifiDirect.WifiDirectConnectionListener {

    @Override
    public void onServiceDiscovered(@NonNull WifiP2pDevice serviceDevice, @NonNull String extraInfo) {
      handler.sendMessage(handler.obtainMessage(CONNECT_TO_SERVICE, Integer.parseInt(extraInfo), 0, serviceDevice.deviceAddress));
    }

    @Override
    public void onNetworkConnected(@NonNull WifiP2pInfo info) {
      if (info.isGroupOwner) {
        handler.sendEmptyMessage(START_IP_EXCHANGE);
      } else {
        handler.sendMessage(handler.obtainMessage(START_NETWORK_CLIENT, info.groupOwnerAddress.getHostAddress()));
      }
    }

    @Override
    public void onNetworkFailure() {
      handler.sendEmptyMessage(NETWORK_DISCONNECTED);
    }

    @Override
    public void onConnectionChanged(@NonNull NetworkInfo networkInfo) {
      handler.sendMessage(handler.obtainMessage(NETWORK_CONNECTION_CHANGED, networkInfo.isConnected()));
    }

  }
}
