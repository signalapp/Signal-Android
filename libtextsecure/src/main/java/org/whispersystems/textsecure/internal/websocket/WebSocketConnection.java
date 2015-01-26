package org.whispersystems.textsecure.internal.websocket;

import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.ws.WebSocket;
import com.squareup.okhttp.internal.ws.WebSocketListener;

import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.internal.util.BlacklistingTrustManager;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import okio.Buffer;
import okio.BufferedSource;

import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketMessage;
import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketResponseMessage;

public class WebSocketConnection {

  private static final String TAG = WebSocketConnection.class.getSimpleName();

  private final LinkedList<WebSocketRequestMessage> incomingRequests = new LinkedList<>();

  private final String     wsUri;
  private final TrustStore trustStore;

  private Client client;

  public WebSocketConnection(String httpUri, TrustStore trustStore, String user, String password) {
    this.trustStore = trustStore;
    this.wsUri      = String.format(httpUri.replace("https://", "wss://")
                                           .replace("http://", "ws://") + "/v1/websocket/?login=%s&password=%s",
                                    user, URLEncoder.encode(password));
  }

  public synchronized void connect() {
    if (client == null) {
      client = new Client(wsUri, trustStore);
      client.connect();
    }
  }

  public synchronized void disconnect() throws IOException {
    if (client != null) {
      client.disconnect();
      client = null;
    }
  }

  public synchronized WebSocketRequestMessage readRequest(long timeoutMillis)
      throws TimeoutException, IOException
  {
    if (client == null) {
      throw new IOException("Connection closed!");
    }

    try {
      long startTime = System.currentTimeMillis();

      while (client != null && incomingRequests.isEmpty() && elapsedTime(startTime) < timeoutMillis) {
        wait(Math.max(1, timeoutMillis - elapsedTime(startTime)));
      }

      if      (incomingRequests.isEmpty() && client == null) throw new IOException("Connection closed!");
      else if (incomingRequests.isEmpty())                   throw new TimeoutException("Timeout exceeded");
      else                                                   return incomingRequests.removeFirst();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public synchronized void sendResponse(WebSocketResponseMessage response) throws IOException {
    WebSocketMessage message = WebSocketMessage.newBuilder()
                                               .setType(WebSocketMessage.Type.RESPONSE)
                                               .setResponse(response)
                                               .build();

    client.sendMessage(message.toByteArray());
  }

  public synchronized void sendRequest(WebSocketRequestMessage request) throws IOException {
    WebSocketMessage message = WebSocketMessage.newBuilder()
                                               .setType(WebSocketMessage.Type.REQUEST)
                                               .setRequest(request)
                                               .build();

    client.sendMessage(message.toByteArray());
  }

  private synchronized void onMessage(byte[] payload) {
    try {
      WebSocketMessage message = WebSocketMessage.parseFrom(payload);

      if      (message.hasRequest())  incomingRequests.add(message.getRequest());
//      else if (message.hasResponse()) notifyResponseReceived(message.getResponse());

      notifyAll();
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, e);
    }
  }

  private synchronized void onClose() {
    if (client != null) {
      client = null;
      connect();
    }

    notifyAll();
  }

  private long elapsedTime(long startTime) {
    return System.currentTimeMillis() - startTime;
  }

  private class Client implements WebSocketListener {

    private final WebSocket webSocket;

    public Client(String uri, TrustStore trustStore) {
      this.webSocket = WebSocket.newWebSocket(new OkHttpClient().setSslSocketFactory(createTlsSocketFactory(trustStore)),
                                              new Request.Builder().url(uri).build());

      Log.w(TAG, "Connecting to: " + uri);
    }

    public void connect() {
      new Thread() {
        @Override
        public void run() {
          while (!webSocket.isClosed()) {
            try {
              Response response = webSocket.connect(Client.this);

              if (response.code() == 101) return;
              else                        Log.w(TAG, "WebSocket Response: " + response.code());
            } catch (IOException e) {
              Log.w(TAG, e);
            }
          }
        }
      }.start();
    }

    public void disconnect() {
      try {
        webSocket.close(1000, "OK");
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    public void sendMessage(byte[] message) throws IOException {
      webSocket.sendMessage(WebSocket.PayloadType.BINARY, new Buffer().write(message));
    }

    @Override
    public void onMessage(BufferedSource payload, WebSocket.PayloadType type) throws IOException {
      if (type.equals(WebSocket.PayloadType.BINARY)) {
        WebSocketConnection.this.onMessage(payload.readByteArray());
      }
    }

    @Override
    public void onClose(int code, String reason) {
      Log.w(TAG, String.format("onClose(%d, %s)", code, reason));
      WebSocketConnection.this.onClose();
    }

    @Override
    public void onFailure(IOException e) {
      Log.w(TAG, e);
      WebSocketConnection.this.onClose();
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
}
