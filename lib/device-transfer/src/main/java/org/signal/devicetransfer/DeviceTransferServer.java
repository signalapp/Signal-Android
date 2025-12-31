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
import org.signal.devicetransfer.SelfSignedIdentity.SelfSignedKeys;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates the logic to advertise the availability of a transfer service over a WiFi Direct
 * network, establish a WiFi Direct network, and then act as a TCP server for a {@link DeviceTransferClient}.
 * <p>
 * Once up an running, the server will continue to run until told to stop. Unlike the client the
 * server has a harder time knowing there are problems and thus doesn't have mitigations to help
 * with connectivity issues. Once connected to a client, the TCP server will run until told to stop.
 * This means that multiple serial connections to it could be made if needed.
 * <p>
 * Testing found that restarting the client worked better than restarting the server when having WiFi
 * Direct setup issues.
 */
final class DeviceTransferServer implements Handler.Callback {

  private static final String TAG = Log.tag(DeviceTransferServer.class);

  private static final int START_SERVER               = 0;
  private static final int STOP_SERVER                = 1;
  private static final int START_IP_EXCHANGE          = 2;
  private static final int IP_EXCHANGE_SUCCESS        = 3;
  private static final int NETWORK_FAILURE            = 4;
  private static final int SET_VERIFIED               = 5;
  private static final int NETWORK_CONNECTION_CHANGED = 6;

  private       NetworkServerThread         serverThread;
  private       HandlerThread               commandAndControlThread;
  private final Handler                     handler;
  private       WifiDirect                  wifiDirect;
  private final Context                     context;
  private final ServerTask                  serverTask;
  private final ShutdownCallback            shutdownCallback;
  private       IpExchange.IpExchangeThread ipExchangeThread;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private static void update(@NonNull TransferStatus transferStatus) {
    Log.d(TAG, "transferStatus: " + transferStatus.getTransferMode().name());
    EventBus.getDefault().postSticky(transferStatus);
  }

  @AnyThread
  public DeviceTransferServer(@NonNull Context context,
                              @NonNull ServerTask serverTask,
                              @Nullable ShutdownCallback shutdownCallback)
  {
    this.context                 = context;
    this.serverTask              = serverTask;
    this.shutdownCallback        = shutdownCallback;
    this.commandAndControlThread = SignalExecutors.getAndStartHandlerThread("server-cnc", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD);
    this.handler                 = new Handler(commandAndControlThread.getLooper(), this);
  }

  @MainThread
  public void start() {
    if (started.compareAndSet(false, true)) {
      update(TransferStatus.ready());
      handler.sendEmptyMessage(START_SERVER);
    }
  }

  @MainThread
  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      handler.sendEmptyMessage(STOP_SERVER);
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
    stopServer();
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
      case START_SERVER:
        startNetworkServer();
        break;
      case STOP_SERVER:
        shutdown();
        break;
      case START_IP_EXCHANGE:
        startIpExchange((String) message.obj);
        break;
      case IP_EXCHANGE_SUCCESS:
        ipExchangeSuccessful();
        break;
      case SET_VERIFIED:
        if (serverThread != null) {
          serverThread.setVerified((Boolean) message.obj);
        }
        break;
      case NETWORK_CONNECTION_CHANGED:
        requestNetworkInfo((Boolean) message.obj);
        break;
      case NetworkServerThread.NETWORK_SERVER_STARTED:
        startWifiDirect(message.arg1);
        break;
      case NetworkServerThread.NETWORK_SERVER_STOPPED:
        update(TransferStatus.shutdown());
        internalShutdown();
        break;
      case NetworkServerThread.NETWORK_CLIENT_CONNECTED:
        stopDiscoveryService();
        update(TransferStatus.serviceConnected());
        break;
      case NetworkServerThread.NETWORK_CLIENT_DISCONNECTED:
        update(TransferStatus.networkConnected());
        break;
      case NetworkServerThread.NETWORK_CLIENT_SSL_ESTABLISHED:
        update(TransferStatus.verificationRequired((Integer) message.obj));
        break;
      default:
        internalShutdown();
        throw new AssertionError("Unknown message: " + message.what);
    }
    return false;
  }

  private void startWifiDirect(int port) {
    if (wifiDirect != null) {
      Log.e(TAG, "Server already started");
      return;
    }

    try {
      wifiDirect = new WifiDirect(context);
      wifiDirect.initialize(new WifiDirectListener());
      wifiDirect.startDiscoveryService(String.valueOf(port));
      Log.i(TAG, "Started discovery service, waiting for connections...");
      update(TransferStatus.discovery());
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

  private void stopDiscoveryService() {
    if (wifiDirect == null) {
      return;
    }

    try {
      Log.i(TAG, "Stopping discovery service");
      wifiDirect.stopDiscoveryService();
    } catch (WifiDirectUnavailableException e) {
      internalShutdown();
      update(TransferStatus.failed());
    }
  }

  private void stopWifiDirect() {
    if (wifiDirect != null) {
      Log.i(TAG, "Shutting down WiFi Direct");
      wifiDirect.shutdown();
      wifiDirect = null;
    }
  }

  private void requestNetworkInfo(boolean isNetworkConnected) {
    if (wifiDirect == null) {
      return;
    }

    if (isNetworkConnected) {
      try {
        wifiDirect.requestNetworkInfo();
      } catch (WifiDirectUnavailableException e) {
        Log.e(TAG, e);
        internalShutdown();
        update(TransferStatus.failed());
      }
    }
  }

  private void startNetworkServer() {
    if (serverThread != null) {
      Log.i(TAG, "Server already running");
      return;
    }

    try {
      update(TransferStatus.startingUp());
      SelfSignedKeys keys = SelfSignedIdentity.create();
      Log.i(TAG, "Spinning up network server.");
      serverThread = new NetworkServerThread(context, serverTask, keys, handler);
      serverThread.start();
    } catch (KeyGenerationFailedException e) {
      Log.w(TAG, "Error generating keys", e);
      internalShutdown();
      update(TransferStatus.failed());
    }
  }

  private void stopServer() {
    if (serverThread != null) {
      Log.i(TAG, "Shutting down ServerThread");
      serverThread.shutdown();
      try {
        serverThread.join(TimeUnit.SECONDS.toMillis(1));
      } catch (InterruptedException e) {
        Log.i(TAG, "Server thread took too long to shutdown", e);
      }
      serverThread = null;
    }
  }

  private void startIpExchange(@NonNull String groupOwnerHostAddress) {
    ipExchangeThread = IpExchange.giveIp(groupOwnerHostAddress, serverThread.getLocalPort(), handler, IP_EXCHANGE_SUCCESS);
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

  private void ipExchangeSuccessful() {
    stopIpExchange();
  }

  final class WifiDirectListener implements WifiDirect.WifiDirectConnectionListener {

    @Override
    public void onNetworkConnected(@NonNull WifiP2pInfo info) {
      if (!info.isGroupOwner) {
        handler.sendMessage(handler.obtainMessage(START_IP_EXCHANGE, info.groupOwnerAddress.getHostAddress()));
      }
    }

    @Override
    public void onNetworkFailure() {
      handler.sendEmptyMessage(NETWORK_FAILURE);
    }

    @Override
    public void onConnectionChanged(@NonNull NetworkInfo networkInfo) {
      handler.sendMessage(handler.obtainMessage(NETWORK_CONNECTION_CHANGED, networkInfo.isConnected()));
    }

    @Override
    public void onServiceDiscovered(@NonNull WifiP2pDevice serviceDevice, @NonNull String extraInfo) { }
  }
}
