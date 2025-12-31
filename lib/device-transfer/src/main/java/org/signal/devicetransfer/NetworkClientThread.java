package org.signal.devicetransfer;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

/**
 * Performs the networking setup/tear down for the client. This includes
 * connecting to the server, performing the TLS/SAS verification, running an
 * arbitrarily provided {@link ClientTask}, and then cleaning up.
 */
final class NetworkClientThread extends Thread {

  private static final String TAG = Log.tag(NetworkClientThread.class);

  public static final int NETWORK_CLIENT_CONNECTED       = 1001;
  public static final int NETWORK_CLIENT_DISCONNECTED    = 1002;
  public static final int NETWORK_CLIENT_SSL_ESTABLISHED = 1003;
  public static final int NETWORK_CLIENT_STOPPED         = 1004;

  private volatile SSLSocket client;
  private volatile boolean   isRunning;
  private volatile Boolean   isVerified;

  private final Context    context;
  private final ClientTask clientTask;
  private final String     serverHostAddress;
  private final int        port;
  private final Handler    handler;
  private final Object     verificationLock;
  private       boolean    success;

  public NetworkClientThread(@NonNull Context context,
                             @NonNull ClientTask clientTask,
                             @NonNull String serverHostAddress,
                             int port,
                             @NonNull Handler handler)
  {
    this.context           = context;
    this.clientTask        = clientTask;
    this.serverHostAddress = serverHostAddress;
    this.port              = port;
    this.handler           = handler;
    this.verificationLock  = new Object();
  }

  @Override
  public void run() {
    Log.i(TAG, "Client thread running");
    isRunning = true;

    int validClientAttemptsRemaining = 3;
    while (shouldKeepRunning()) {
      Log.i(TAG, "Attempting to connect to server... tries: " + validClientAttemptsRemaining);

      try {
        SelfSignedIdentity.ApprovingTrustManager trustManager = new SelfSignedIdentity.ApprovingTrustManager();
        client = (SSLSocket) SelfSignedIdentity.getApprovingSocketFactory(trustManager).createSocket();
        try {
          client.bind(null);
          client.connect(new InetSocketAddress(serverHostAddress, port), 10000);
          client.startHandshake();

          X509Certificate x509 = trustManager.getX509Certificate();
          if (x509 == null) {
            isRunning = false;
            throw new SSLHandshakeException("no x509 after handshake");
          }

          InputStream  inputStream        = client.getInputStream();
          OutputStream outputStream       = client.getOutputStream();
          int          authenticationCode = DeviceTransferAuthentication.generateClientAuthenticationCode(x509.getEncoded(), inputStream, outputStream);

          handler.sendMessage(handler.obtainMessage(NETWORK_CLIENT_SSL_ESTABLISHED, authenticationCode));

          Log.i(TAG, "Waiting for user to verify sas");
          awaitAuthenticationCodeVerification();
          Log.d(TAG, "Waiting for server to tell us they also verified");
          outputStream.write(0x43);
          outputStream.flush();
          try {
            int result = inputStream.read();
            if (result == -1) {
              Log.w(TAG, "Something happened waiting for server to verify");
              throw new DeviceTransferAuthentication.DeviceTransferAuthenticationException("server disconnected while we waited");
            }
          } catch (IOException e) {
            Log.w(TAG, "Something happened waiting for server to verify", e);
            throw new DeviceTransferAuthentication.DeviceTransferAuthenticationException(e);
          }

          handler.sendEmptyMessage(NETWORK_CLIENT_CONNECTED);
          clientTask.run(context, outputStream);
          outputStream.flush();

          Log.d(TAG, "Waiting for server to tell us they got everything");
          try {
            //noinspection ResultOfMethodCallIgnored
            inputStream.read();
          } catch (IOException e) {
            Log.w(TAG, "Something happened confirming with server, mostly like bad SSL shutdown state, assuming success", e);
          }
          success   = true;
          isRunning = false;
        } catch (IOException e) {
          Log.w(TAG, "Error connecting to server", e);
          validClientAttemptsRemaining--;
          isRunning = validClientAttemptsRemaining > 0;
        }
      } catch (Exception e) {
        Log.w(TAG, e);
        isRunning = false;
      } finally {
        if (success) {
          clientTask.success();
        }
        StreamUtil.close(client);
        handler.sendEmptyMessage(NETWORK_CLIENT_DISCONNECTED);
      }

      if (shouldKeepRunning()) {
        ThreadUtil.interruptableSleep(TimeUnit.SECONDS.toMillis(3));
      }
    }

    Log.i(TAG, "Client exiting");
    handler.sendEmptyMessage(NETWORK_CLIENT_STOPPED);
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
    StreamUtil.close(client);
    interrupt();
  }

  private boolean shouldKeepRunning() {
    return !isInterrupted() && isRunning;
  }
}
