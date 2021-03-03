package org.signal.devicetransfer;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates the logic to find and establish a WiFi Direct connection with another
 * device and then perform an arbitrary {@link ClientTask} with the TCP socket.
 * <p>
 * The client attempts multiple times to establish the network and deal with connectivity
 * problems. It will also retry the task if an issue occurs while running it.
 * <p>
 * The client is setup to retry indefinitely and will only bail on its own if it's
 * unable to start {@link WifiDirect}. A call to {@link #shutdown()} is required to
 * stop client from the "outside."
 * <p>
 * Summary of mitigations:
 * <ul>
 *   <li>Completely tear down and restart WiFi direct if no server is found within the timeout.</li>
 *   <li>Retry connecting to the WiFi Direct network, and after all retries fail it does a complete tear down and restart.</li>
 *   <li>Retry connecting to the server until successful, disconnected from WiFi Direct network, or told to stop.</li>
 * </ul>
 */
public final class DeviceTransferClient implements Handler.Callback {

  private static final String TAG                  = Log.tag(DeviceTransferClient.class);
  private static final int    START_CLIENT         = 0;
  private static final int    START_NETWORK_CLIENT = 1;
  private static final int    NETWORK_DISCONNECTED = 2;
  private static final int    CONNECT_TO_SERVICE   = 3;
  private static final int    RESTART_CLIENT       = 4;
  private static final int    START_IP_EXCHANGE    = 5;
  private static final int    IP_EXCHANGE_SUCCESS  = 6;

  private final Context                     context;
  private final int                         port;
  private       HandlerThread               commandAndControlThread;
  private final Handler                     handler;
  private final ClientTask                  clientTask;
  private final ShutdownCallback            shutdownCallback;
  private       WifiDirect                  wifiDirect;
  private       ClientThread                clientThread;
  private final Runnable                    autoRestart;
  private       IpExchange.IpExchangeThread ipExchangeThread;

  private static void update(@NonNull TransferMode transferMode) {
    EventBus.getDefault().postSticky(transferMode);
  }

  @AnyThread
  public DeviceTransferClient(@NonNull Context context, @NonNull ClientTask clientTask, int port, @Nullable ShutdownCallback shutdownCallback) {
    this.context                 = context;
    this.clientTask              = clientTask;
    this.port                    = port;
    this.shutdownCallback        = shutdownCallback;
    this.commandAndControlThread = SignalExecutors.getAndStartHandlerThread("client-cnc");
    this.handler                 = new Handler(commandAndControlThread.getLooper(), this);
    this.autoRestart             = () -> {
      Log.i(TAG, "Restarting WiFi Direct since we haven't found anything yet and it could be us.");
      handler.sendEmptyMessage(RESTART_CLIENT);
    };
  }

  @AnyThread
  public void start() {
    handler.sendMessage(handler.obtainMessage(START_CLIENT));
  }

  @AnyThread
  public synchronized void shutdown() {
    stopIpExchange();
    stopClient();
    stopWifiDirect();

    if (commandAndControlThread != null) {
      Log.i(TAG, "Shutting down command and control");
      commandAndControlThread.quit();
      commandAndControlThread.interrupt();
      commandAndControlThread = null;
    }
  }

  @Override
  public boolean handleMessage(@NonNull Message message) {
    switch (message.what) {
      case START_CLIENT:
        startWifiDirect();
        break;
      case START_NETWORK_CLIENT:
        startClient((String) message.obj);
        break;
      case NETWORK_DISCONNECTED:
        stopClient();
        break;
      case CONNECT_TO_SERVICE:
        connectToService((String) message.obj);
        break;
      case RESTART_CLIENT:
        stopClient();
        stopWifiDirect();
        startWifiDirect();
        break;
      case START_IP_EXCHANGE:
        startIpExchange((String) message.obj);
        break;
      case IP_EXCHANGE_SUCCESS:
        ipExchangeSuccessful((String) message.obj);
        break;
      default:
        shutdown();
        if (shutdownCallback != null) {
          shutdownCallback.shutdown();
        }
        throw new AssertionError();
    }
    return false;
  }

  private void startWifiDirect() {
    if (wifiDirect != null) {
      Log.e(TAG, "Client already started");
      return;
    }

    update(TransferMode.STARTING_UP);

    try {
      wifiDirect = new WifiDirect(context);
      wifiDirect.initialize(new WifiDirectListener());
      wifiDirect.discoverService();
      Log.i(TAG, "Started service discovery, searching for service...");
      update(TransferMode.DISCOVERY);
      handler.postDelayed(autoRestart, TimeUnit.SECONDS.toMillis(15));
    } catch (WifiDirectUnavailableException e) {
      Log.e(TAG, e);
      shutdown();
      update(TransferMode.FAILED);
      if (shutdownCallback != null) {
        shutdownCallback.shutdown();
      }
    }
  }

  private void stopWifiDirect() {
    handler.removeCallbacks(autoRestart);

    if (wifiDirect != null) {
      Log.i(TAG, "Shutting down WiFi Direct");
      wifiDirect.shutdown();
      wifiDirect = null;
      update(TransferMode.READY);
    }
  }

  private void startClient(@NonNull String serverHostAddress) {
    if (clientThread != null) {
      Log.i(TAG, "Client already running");
      return;
    }

    Log.i(TAG, "Connection established, spinning up network client.");
    clientThread = new ClientThread(context, clientTask, serverHostAddress, port);
    clientThread.start();
  }

  private void stopClient() {
    if (clientThread != null) {
      Log.i(TAG, "Shutting down ClientThread");
      clientThread.shutdown();
      clientThread = null;
    }
  }

  private void connectToService(@NonNull String deviceAddress) {
    if (wifiDirect == null) {
      Log.w(TAG, "WifiDirect is not initialized, we shouldn't be here.");
      return;
    }

    handler.removeCallbacks(autoRestart);

    int tries = 5;
    while ((tries--) > 0) {
      try {
        wifiDirect.connect(deviceAddress);
        update(TransferMode.NETWORK_CONNECTED);
        return;
      } catch (WifiDirectUnavailableException e) {
        Log.w(TAG, "Unable to connect, tries: " + tries);
        ThreadUtil.sleep(TimeUnit.SECONDS.toMillis(2));
      }
    }

    handler.sendMessage(handler.obtainMessage(RESTART_CLIENT));
  }

  private void startIpExchange(@NonNull String groupOwnerHostAddress) {
    ipExchangeThread = IpExchange.getIp(groupOwnerHostAddress, port, handler, IP_EXCHANGE_SUCCESS);
  }

  private void stopIpExchange() {
    if (ipExchangeThread != null) {
      ipExchangeThread.shutdown();
      ipExchangeThread = null;
    }
  }

  private void ipExchangeSuccessful(@NonNull String host) {
    stopIpExchange();
    handler.sendMessage(handler.obtainMessage(START_NETWORK_CLIENT, host));
  }

  private static class ClientThread extends Thread {

    private volatile Socket  client;
    private volatile boolean isRunning;

    private final Context    context;
    private final ClientTask clientTask;
    private final String     serverHostAddress;
    private final int        port;

    public ClientThread(@NonNull Context context,
                        @NonNull ClientTask clientTask,
                        @NonNull String serverHostAddress,
                        int port)
    {
      this.context           = context;
      this.clientTask        = clientTask;
      this.serverHostAddress = serverHostAddress;
      this.port              = port;
    }

    @Override
    public void run() {
      Log.i(TAG, "Client thread running");
      isRunning = true;

      while (shouldKeepRunning()) {
        Log.i(TAG, "Attempting to connect to server...");

        try {
          client = new Socket();
          try {
            client.bind(null);
            client.connect(new InetSocketAddress(serverHostAddress, port), 10000);
            DeviceTransferClient.update(TransferMode.SERVICE_CONNECTED);

            clientTask.run(context, client.getOutputStream());

            Log.i(TAG, "Done!!");
            isRunning = false;
          } catch (IOException e) {
            Log.w(TAG, "Error connecting to server", e);
          }
        } catch (Exception e) {
          Log.w(TAG, e);
        } finally {
          if (client != null && !client.isClosed()) {
            try {
              client.close();
            } catch (IOException ignored) {}
          }
          DeviceTransferClient.update(TransferMode.NETWORK_CONNECTED);
        }

        if (shouldKeepRunning()) {
          ThreadUtil.interruptableSleep(TimeUnit.SECONDS.toMillis(3));
        }
      }

      Log.i(TAG, "Client exiting");
    }

    public void shutdown() {
      isRunning = false;
      try {
        if (client != null) {
          client.close();
        }
      } catch (IOException e) {
        Log.w(TAG, "Error shutting down client socket", e);
      }
      interrupt();
    }

    private boolean shouldKeepRunning() {
      return !isInterrupted() && isRunning;
    }
  }

  public class WifiDirectListener implements WifiDirect.WifiDirectConnectionListener {

    @Override
    public void onServiceDiscovered(@NonNull WifiP2pDevice serviceDevice) {
      handler.sendMessage(handler.obtainMessage(CONNECT_TO_SERVICE, serviceDevice.deviceAddress));
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
    public void onNetworkDisconnected() {
      handler.sendEmptyMessage(NETWORK_DISCONNECTED);
    }

    @Override
    public void onNetworkFailure() {
      handler.sendEmptyMessage(NETWORK_DISCONNECTED);
    }

    @Override
    public void onLocalDeviceChanged(@NonNull WifiP2pDevice localDevice) { }
  }
}
