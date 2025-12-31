package org.signal.devicetransfer;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Performs the networking setup/tear down for the server. This includes
 * connecting to the client, generating TLS keys, performing the TLS/SAS verification,
 * running an arbitrarily provided {@link ServerTask}, and then cleaning up.
 */
final class NetworkServerThread extends Thread {

  private static final String TAG = Log.tag(NetworkServerThread.class);

  public static final int NETWORK_SERVER_STARTED         = 1001;
  public static final int NETWORK_SERVER_STOPPED         = 1002;
  public static final int NETWORK_CLIENT_CONNECTED       = 1003;
  public static final int NETWORK_CLIENT_DISCONNECTED    = 1004;
  public static final int NETWORK_CLIENT_SSL_ESTABLISHED = 1005;

  private volatile ServerSocket serverSocket;
  private volatile Socket       clientSocket;
  private volatile boolean      isRunning;
  private volatile Boolean      isVerified;

  private final Context                           context;
  private final ServerTask                        serverTask;
  private final SelfSignedIdentity.SelfSignedKeys keys;
  private final Handler                           handler;
  private final Object                            verificationLock;

  public NetworkServerThread(@NonNull Context context,
                             @NonNull ServerTask serverTask,
                             @NonNull SelfSignedIdentity.SelfSignedKeys keys,
                             @NonNull Handler handler)
  {
    this.context          = context;
    this.serverTask       = serverTask;
    this.keys             = keys;
    this.handler          = handler;
    this.verificationLock = new Object();
  }

  @Override
  public void run() {
    Log.i(TAG, "Server thread running");
    isRunning = true;

    Log.i(TAG, "Starting up server socket...");
    try {
      serverSocket = SelfSignedIdentity.getServerSocketFactory(keys).createServerSocket(0);
      handler.sendMessage(handler.obtainMessage(NETWORK_SERVER_STARTED, serverSocket.getLocalPort(), 0));
      while (shouldKeepRunning() && !serverSocket.isClosed()) {
        Log.i(TAG, "Waiting for client socket accept...");
        try {
          clientSocket = serverSocket.accept();

          if (!isRunning) {
            break;
          }

          InputStream  inputStream        = clientSocket.getInputStream();
          OutputStream outputStream       = clientSocket.getOutputStream();
          int          authenticationCode = DeviceTransferAuthentication.generateServerAuthenticationCode(keys.getX509Encoded(), inputStream, outputStream);

          handler.sendMessage(handler.obtainMessage(NETWORK_CLIENT_SSL_ESTABLISHED, authenticationCode));

          Log.i(TAG, "Waiting for user to verify sas");
          awaitAuthenticationCodeVerification();
          Log.d(TAG, "Waiting for client to tell us they also verified");
          outputStream.write(0x43);
          outputStream.flush();
          try {
            int result = inputStream.read();
            if (result == -1) {
              Log.w(TAG, "Something happened waiting for client to verify");
              throw new DeviceTransferAuthentication.DeviceTransferAuthenticationException("client disconnected while we waited");
            }
          } catch (IOException e) {
            Log.w(TAG, "Something happened waiting for client to verify", e);
            throw new DeviceTransferAuthentication.DeviceTransferAuthenticationException(e);
          }

          handler.sendEmptyMessage(NETWORK_CLIENT_CONNECTED);
          serverTask.run(context, inputStream);

          outputStream.write(0x53);
          outputStream.flush();
        } catch (IOException e) {
          if (isRunning) {
            Log.i(TAG, "Error connecting with client or server socket closed.", e);
          } else {
            Log.i(TAG, "Server shutting down...");
          }
        } finally {
          StreamUtil.close(clientSocket);
          handler.sendEmptyMessage(NETWORK_CLIENT_DISCONNECTED);
        }
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      Log.w(TAG, e);
    } finally {
      StreamUtil.close(serverSocket);
    }

    Log.i(TAG, "Server exiting");
    isRunning = false;
    handler.sendEmptyMessage(NETWORK_SERVER_STOPPED);
  }

  private void awaitAuthenticationCodeVerification() throws DeviceTransferAuthentication.DeviceTransferAuthenticationException {
    synchronized (verificationLock) {
      try {
        while (isVerified == null) {
          verificationLock.wait();
        }
        if (!isVerified) {
          throw new DeviceTransferAuthentication.DeviceTransferAuthenticationException("User verification failed");
        }
      } catch (InterruptedException e) {
        throw new DeviceTransferAuthentication.DeviceTransferAuthenticationException(e);
      }
    }
  }

  private boolean shouldKeepRunning() {
    return !isInterrupted() && isRunning;
  }

  @AnyThread
  public int getLocalPort() {
    ServerSocket localServerSocket = serverSocket;
    if (localServerSocket != null) {
      return localServerSocket.getLocalPort();
    }
    return 0;
  }

  @AnyThread
  public void setVerified(boolean isVerified) {
    this.isVerified = isVerified;
    synchronized (verificationLock) {
      verificationLock.notify();
    }
  }

  @AnyThread
  public void shutdown() {
    isRunning = false;
    StreamUtil.close(clientSocket);
    StreamUtil.close(serverSocket);
    interrupt();
  }
}
