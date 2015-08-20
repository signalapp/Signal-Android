package org.whispersystems.textsecure.internal.websocket;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.ws.WebSocket;
import com.squareup.okhttp.internal.ws.WebSocketListener;

import org.whispersystems.libaxolotl.logging.Log;
import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.api.util.CredentialsProvider;
import org.whispersystems.textsecure.internal.util.BlacklistingTrustManager;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import okio.Buffer;
import okio.BufferedSource;

public class OkHttpClientWrapper implements WebSocketListener {

  private static final String TAG = OkHttpClientWrapper.class.getSimpleName();

  private final String                 uri;
  private final TrustStore             trustStore;
  private final CredentialsProvider    credentialsProvider;
  private final WebSocketEventListener listener;
  private final String                 userAgent;

  private WebSocket webSocket;
  private boolean   closed;
  private boolean   connected;

  public OkHttpClientWrapper(String uri, TrustStore trustStore,
                             CredentialsProvider credentialsProvider,
                             String userAgent,
                             WebSocketEventListener listener)
  {
    Log.w(TAG, "Connecting to: " + uri);

    this.uri                 = uri;
    this.trustStore          = trustStore;
    this.credentialsProvider = credentialsProvider;
    this.userAgent           = userAgent;
    this.listener            = listener;
  }

  public void connect(final int timeout, final TimeUnit timeUnit) {
    new Thread() {
      @Override
      public void run() {
        int attempt = 0;

        while ((webSocket = newSocket(timeout, timeUnit)) != null) {
          try {
            Response response = webSocket.connect(OkHttpClientWrapper.this);

            if (response.code() == 101) {
              synchronized (OkHttpClientWrapper.this) {
                if (closed) webSocket.close(1000, "OK");
                else        connected = true;
              }

              listener.onConnected();
              return;
            }

            Log.w(TAG, "WebSocket Response: " + response.code());
          } catch (IOException e) {
            Log.w(TAG, e);
          }

          Util.sleep(Math.min(++attempt * 200, TimeUnit.SECONDS.toMillis(15)));
        }
      }
    }.start();
  }

  public synchronized void disconnect() {
    Log.w(TAG, "Calling disconnect()...");
    try {
      closed = true;
      if (webSocket != null && connected) {
        webSocket.close(1000, "OK");
      }
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void sendMessage(byte[] message) throws IOException {
    webSocket.sendMessage(WebSocket.PayloadType.BINARY, new Buffer().write(message));
  }

  @Override
  public void onMessage(BufferedSource payload, WebSocket.PayloadType type) throws IOException {
    Log.w(TAG, "onMessage: " + type);
    if (type.equals(WebSocket.PayloadType.BINARY)) {
      listener.onMessage(payload.readByteArray());
    }

    payload.close();
  }

  @Override
  public void onClose(int code, String reason) {
    Log.w(TAG, String.format("onClose(%d, %s)", code, reason));
    listener.onClose();
  }

  @Override
  public void onFailure(IOException e) {
    Log.w(TAG, e);
    listener.onClose();
  }

  private synchronized WebSocket newSocket(int timeout, TimeUnit unit) {
    if (closed) return null;

    String       filledUri    = String.format(uri, credentialsProvider.getUser(), credentialsProvider.getPassword());
    OkHttpClient okHttpClient = new OkHttpClient();

    okHttpClient.setSslSocketFactory(createTlsSocketFactory(trustStore));
    okHttpClient.setReadTimeout(timeout, unit);
    okHttpClient.setConnectTimeout(timeout, unit);

    Request.Builder requestBuilder = new Request.Builder().url(filledUri);

    if (userAgent != null) {
      requestBuilder.addHeader("X-Signal-Agent", userAgent);
    }

    return WebSocket.newWebSocket(okHttpClient, requestBuilder.build());
  }

  private SSLSocketFactory createTlsSocketFactory(TrustStore trustStore) {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, BlacklistingTrustManager.createFor(trustStore), null);

      return context.getSocketFactory();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

}
