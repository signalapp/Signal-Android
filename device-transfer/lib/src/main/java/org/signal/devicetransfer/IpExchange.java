package org.signal.devicetransfer;

import android.os.Handler;

import androidx.annotation.NonNull;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * A WiFi Direct group is created auto-magically when connecting and randomly determines the group owner.
 * Only the group owner's host address is exposed via the WiFi Direct APIs and thus sometimes the client
 * is selected as the group owner and is unable to know the host address of the server.
 *
 * When this occurs, {@link #giveIp(String, int, Handler, int)} and {@link #getIp(String, int, Handler, int)} allow
 * the two to connect briefly and use the connected socket to determine the host address of the other.
 */
final class IpExchange {

  private IpExchange() { }

  public static @NonNull IpExchangeThread giveIp(@NonNull String host, int port, @NonNull Handler handler, int message) {
    IpExchangeThread thread = new IpExchangeThread(host, port, false, handler, message);
    thread.start();
    return thread;
  }

  public static @NonNull IpExchangeThread getIp(@NonNull String host, int port, @NonNull Handler handler, int message) {
    IpExchangeThread thread = new IpExchangeThread(host, port, true, handler, message);
    thread.start();
    return thread;
  }

  public static class IpExchangeThread extends Thread {

    private static final String TAG = Log.tag(IpExchangeThread.class);

    private volatile ServerSocket serverSocket;
    private volatile Socket       client;
    private volatile boolean      isRunning;

    private final String  serverHostAddress;
    private final int     port;
    private final boolean needsIp;
    private final Handler handler;
    private final int     message;

    public IpExchangeThread(@NonNull String serverHostAddress, int port, boolean needsIp, @NonNull Handler handler, int message) {
      this.serverHostAddress = serverHostAddress;
      this.port              = port;
      this.needsIp           = needsIp;
      this.handler           = handler;
      this.message           = message;
    }

    @Override
    public void run() {
      Log.i(TAG, "Running...");
      isRunning = true;

      while (shouldKeepRunning()) {
        Log.i(TAG, "Attempting to startup networking...");

        try {
          if (needsIp) {
            getIp();
          } else {
            sendIp();
          }
        } catch (Exception e) {
          Log.w(TAG, e);
        } finally {
          if (client != null && !client.isClosed()) {
            try {
              client.close();
            } catch (IOException ignored) {}
          }

          if (serverSocket != null) {
            try {
              serverSocket.close();
            } catch (IOException ignored) {}
          }
        }

        if (shouldKeepRunning()) {
          ThreadUtil.interruptableSleep(TimeUnit.SECONDS.toMillis(3));
        }
      }

      Log.i(TAG, "Exiting");
    }

    private void sendIp() throws IOException {
      client = new Socket();
      client.bind(null);
      client.connect(new InetSocketAddress(serverHostAddress, port), 10000);
      handler.sendEmptyMessage(message);
      Log.i(TAG, "Done!!");
      isRunning = false;
    }

    private void getIp() throws IOException {
      serverSocket = new ServerSocket(port);
      while (shouldKeepRunning() && !serverSocket.isClosed()) {
        Log.i(TAG, "Waiting for client socket accept...");
        try (Socket socket = serverSocket.accept()) {
          Log.i(TAG, "Client connected, obtaining IP address");
          String peerHostAddress = socket.getInetAddress().getHostAddress();
          handler.sendMessage(handler.obtainMessage(message, peerHostAddress));
        } catch (IOException e) {
          if (isRunning) {
            Log.i(TAG, "Error connecting with client or server socket closed.", e);
          } else {
            Log.i(TAG, "Server shutting down...");
          }
        }
      }
    }

    public void shutdown() {
      isRunning = false;
      try {
        if (client != null) {
          client.close();
        }

        if (serverSocket != null) {
          serverSocket.close();
        }
      } catch (IOException e) {
        Log.w(TAG, "Error shutting down", e);
      }
      interrupt();
    }

    private boolean shouldKeepRunning() {
      return !isInterrupted() && isRunning;
    }
  }
}
