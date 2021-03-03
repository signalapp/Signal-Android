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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

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
public final class DeviceTransferServer implements Handler.Callback {

  private static final String TAG                  = Log.tag(DeviceTransferServer.class);
  private static final int    START_SERVER         = 0;
  private static final int    START_NETWORK_SERVER = 1;
  private static final int    NETWORK_DISCONNECTED = 2;
  private static final int    START_IP_EXCHANGE    = 3;
  private static final int    IP_EXCHANGE_SUCCESS  = 4;

  private       ServerThread                serverThread;
  private       HandlerThread               commandAndControlThread;
  private final Handler                     handler;
  private       WifiDirect                  wifiDirect;
  private final Context                     context;
  private final ServerTask                  serverTask;
  private final int                         port;
  private final ShutdownCallback            shutdownCallback;
  private       IpExchange.IpExchangeThread ipExchangeThread;

  private static void update(@NonNull TransferMode transferMode) {
    EventBus.getDefault().postSticky(transferMode);
  }

  @AnyThread
  public DeviceTransferServer(@NonNull Context context, @NonNull ServerTask serverTask, int port, @Nullable ShutdownCallback shutdownCallback) {
    this.context                 = context;
    this.serverTask              = serverTask;
    this.port                    = port;
    this.shutdownCallback        = shutdownCallback;
    this.commandAndControlThread = SignalExecutors.getAndStartHandlerThread("server-cnc");
    this.handler                 = new Handler(commandAndControlThread.getLooper(), this);
  }

  @AnyThread
  public void start() {
    handler.sendMessage(handler.obtainMessage(START_SERVER));
  }

  @AnyThread
  public synchronized void shutdown() {
    stopIpExchange();
    stopServer();
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
      case START_SERVER:
        startWifiDirect();
        break;
      case START_NETWORK_SERVER:
        startServer();
        break;
      case NETWORK_DISCONNECTED:
        stopServer();
        break;
      case START_IP_EXCHANGE:
        startIpExchange((String) message.obj);
        break;
      case IP_EXCHANGE_SUCCESS:
        ipExchangeSuccessful();
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
      Log.e(TAG, "Server already started");
      return;
    }

    update(TransferMode.STARTING_UP);

    try {
      wifiDirect = new WifiDirect(context);
      wifiDirect.initialize(new WifiDirectListener());
      wifiDirect.startDiscoveryService();
      Log.i(TAG, "Started discovery service, waiting for connections...");
      update(TransferMode.DISCOVERY);
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
    if (wifiDirect != null) {
      Log.i(TAG, "Shutting down WiFi Direct");
      wifiDirect.shutdown();
      wifiDirect = null;
      update(TransferMode.READY);
    }
  }

  private void startServer() {
    if (serverThread != null) {
      Log.i(TAG, "Server already running");
      return;
    }

    Log.i(TAG, "Connection established, spinning up network server.");
    serverThread = new ServerThread(context, serverTask, port);
    serverThread.start();

    update(TransferMode.NETWORK_CONNECTED);
  }

  private void stopServer() {
    if (serverThread != null) {
      Log.i(TAG, "Shutting down ServerThread");
      serverThread.shutdown();
      serverThread = null;
    }
  }

  private void startIpExchange(@NonNull String groupOwnerHostAddress) {
    ipExchangeThread = IpExchange.giveIp(groupOwnerHostAddress, port, handler, IP_EXCHANGE_SUCCESS);
  }

  private void stopIpExchange() {
    if (ipExchangeThread != null) {
      ipExchangeThread.shutdown();
      ipExchangeThread = null;
    }
  }

  private void ipExchangeSuccessful() {
    stopIpExchange();
    handler.sendEmptyMessage(START_NETWORK_SERVER);
  }

  public static class ServerThread extends Thread {

    private final    Context      context;
    private final    ServerTask   serverTask;
    private final    int          port;
    private volatile ServerSocket serverSocket;
    private volatile boolean      isRunning;

    public ServerThread(@NonNull Context context, @NonNull ServerTask serverTask, int port) {
      this.context    = context;
      this.serverTask = serverTask;
      this.port       = port;
    }

    @Override
    public void run() {
      Log.i(TAG, "Server thread running");
      isRunning = true;

      while (shouldKeepRunning()) {
        Log.i(TAG, "Starting up server socket...");
        try {
          serverSocket = new ServerSocket(port);
          while (shouldKeepRunning() && !serverSocket.isClosed()) {
            Log.i(TAG, "Waiting for client socket accept...");
            try {
              handleClient(serverSocket.accept());
            } catch (IOException e) {
              if (isRunning) {
                Log.i(TAG, "Error connecting with client or server socket closed.", e);
              } else {
                Log.i(TAG, "Server shutting down...");
              }
            } finally {
              update(TransferMode.NETWORK_CONNECTED);
            }
          }
        } catch (Exception e) {
          Log.w(TAG, e);
        } finally {
          if (serverSocket != null && !serverSocket.isClosed()) {
            try {
              serverSocket.close();
            } catch (IOException ignored) {}
          }
          update(TransferMode.NETWORK_CONNECTED);
        }

        if (shouldKeepRunning()) {
          ThreadUtil.interruptableSleep(TimeUnit.SECONDS.toMillis(3));
        }
      }

      Log.i(TAG, "Server exiting");
    }

    public void shutdown() {
      isRunning = false;
      try {
        serverSocket.close();
      } catch (IOException e) {
        Log.w(TAG, "Error shutting down server socket", e);
      }
      interrupt();
    }

    private void handleClient(@NonNull Socket clientSocket) throws IOException {
      update(TransferMode.SERVICE_CONNECTED);
      serverTask.run(context, clientSocket.getInputStream());
      clientSocket.close();
    }

    private boolean shouldKeepRunning() {
      return !isInterrupted() && isRunning;
    }
  }

  public class WifiDirectListener implements WifiDirect.WifiDirectConnectionListener {

    @Override
    public void onNetworkConnected(@NonNull WifiP2pInfo info) {
      if (info.isGroupOwner) {
        handler.sendEmptyMessage(START_NETWORK_SERVER);
      } else {
        handler.sendMessage(handler.obtainMessage(START_IP_EXCHANGE, info.groupOwnerAddress.getHostAddress()));
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

    @Override
    public void onServiceDiscovered(@NonNull WifiP2pDevice serviceDevice) { }
  }
}
