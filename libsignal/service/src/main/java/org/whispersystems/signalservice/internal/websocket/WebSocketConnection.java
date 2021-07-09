package org.whispersystems.signalservice.internal.websocket;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.SingleSubject;
import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketMessage;
import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketResponseMessage;

public class WebSocketConnection extends WebSocketListener {

  private static final String TAG                       = WebSocketConnection.class.getSimpleName();
  private static final int    KEEPALIVE_TIMEOUT_SECONDS = 55;

  private final LinkedList<WebSocketRequestMessage> incomingRequests = new LinkedList<>();
  private final Map<Long, OutgoingRequest>          outgoingRequests = new HashMap<>();

  private final String                        name;
  private final String                        wsUri;
  private final TrustStore                    trustStore;
  private final Optional<CredentialsProvider> credentialsProvider;
  private final String                        signalAgent;
  private       ConnectivityListener          listener;
  private final SleepTimer                    sleepTimer;
  private final List<Interceptor>             interceptors;
  private final Optional<Dns>                 dns;
  private final Optional<SignalProxy>         signalProxy;

  private WebSocket       client;
  private KeepAliveSender keepAliveSender;
  private int             attempts;
  private boolean         connected;

  public WebSocketConnection(String name,
                             SignalServiceConfiguration serviceConfiguration,
                             Optional<CredentialsProvider> credentialsProvider,
                             String signalAgent,
                             ConnectivityListener listener,
                             SleepTimer timer)
  {
    this.name                = "[" + name + ":" + System.identityHashCode(this) + "]";
    this.trustStore          = serviceConfiguration.getSignalServiceUrls()[0].getTrustStore();
    this.credentialsProvider = credentialsProvider;
    this.signalAgent         = signalAgent;
    this.listener            = listener;
    this.sleepTimer          = timer;
    this.interceptors        = serviceConfiguration.getNetworkInterceptors();
    this.dns                 = serviceConfiguration.getDns();
    this.signalProxy         = serviceConfiguration.getSignalProxy();
    this.attempts            = 0;
    this.connected           = false;

    String uri = serviceConfiguration.getSignalServiceUrls()[0].getUrl().replace("https://", "wss://").replace("http://", "ws://");

    if (credentialsProvider.isPresent()) {
      this.wsUri = uri + "/v1/websocket/?login=%s&password=%s";
    } else {
      this.wsUri = uri + "/v1/websocket/";
    }
  }

  public synchronized void connect() {
    log("connect()");

    if (client == null) {
      String filledUri;

      if (credentialsProvider.isPresent()) {
        String identifier = credentialsProvider.get().getUuid() != null ? credentialsProvider.get().getUuid().toString() : credentialsProvider.get().getE164();
        filledUri = String.format(wsUri, identifier, credentialsProvider.get().getPassword());
      } else {
        filledUri = wsUri;
      }

      Pair<SSLSocketFactory, X509TrustManager> socketFactory = createTlsSocketFactory(trustStore);

      OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                                                           .sslSocketFactory(new Tls12SocketFactory(socketFactory.first()), socketFactory.second())
                                                           .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
                                                           .readTimeout(KEEPALIVE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS)
                                                           .dns(dns.or(Dns.SYSTEM))
                                                           .connectTimeout(KEEPALIVE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);

      for (Interceptor interceptor : interceptors) {
        clientBuilder.addInterceptor(interceptor);
      }

      if (signalProxy.isPresent()) {
        clientBuilder.socketFactory(new TlsProxySocketFactory(signalProxy.get().getHost(), signalProxy.get().getPort(), dns));
      }

      OkHttpClient okHttpClient = clientBuilder.build();

      Request.Builder requestBuilder = new Request.Builder().url(filledUri);

      if (signalAgent != null) {
        requestBuilder.addHeader("X-Signal-Agent", signalAgent);
      }

      if (listener != null) {
        listener.onConnecting();
      }

      this.connected = false;
      this.client    = okHttpClient.newWebSocket(requestBuilder.build(), this);
    }
  }

  public synchronized boolean isDead() {
    return client == null;
  }

  public synchronized void disconnect() {
    log("disconnect()");

    if (client != null) {
      client.close(1000, "OK");
      client    = null;
      connected = false;
    }

    if (keepAliveSender != null) {
      keepAliveSender.shutdown();
      keepAliveSender = null;
    }

    if (listener != null) {
      listener.onDisconnected();
      listener = null;
    }

    notifyAll();
  }

  public synchronized WebSocketRequestMessage readRequest(long timeoutMillis)
      throws TimeoutException, IOException
  {
    if (client == null) {
      throw new IOException("Connection closed!");
    }

    long startTime = System.currentTimeMillis();

    while (client != null && incomingRequests.isEmpty() && elapsedTime(startTime) < timeoutMillis) {
      Util.wait(this, Math.max(1, timeoutMillis - elapsedTime(startTime)));
    }

    if (incomingRequests.isEmpty() && client == null) {
      throw new IOException("Connection closed!");
    } else if (incomingRequests.isEmpty()) {
      throw new TimeoutException("Timeout exceeded");
    } else {
      return incomingRequests.removeFirst();
    }
  }

  public synchronized Single<WebsocketResponse> sendRequest(WebSocketRequestMessage request) throws IOException {
    if (client == null || !connected) {
      throw new IOException("No connection!");
    }

    WebSocketMessage message = WebSocketMessage.newBuilder()
                                               .setType(WebSocketMessage.Type.REQUEST)
                                               .setRequest(request)
                                               .build();

    SingleSubject<WebsocketResponse> single = SingleSubject.create();

    outgoingRequests.put(request.getId(), new OutgoingRequest(single));

    if (!client.send(ByteString.of(message.toByteArray()))) {
      throw new IOException("Write failed!");
    }

    return single.subscribeOn(Schedulers.io())
                 .observeOn(Schedulers.io())
                 .timeout(10, TimeUnit.SECONDS);
  }

  public synchronized void sendResponse(WebSocketResponseMessage response) throws IOException {
    if (client == null) {
      throw new IOException("Connection closed!");
    }

    WebSocketMessage message = WebSocketMessage.newBuilder()
                                               .setType(WebSocketMessage.Type.RESPONSE)
                                               .setResponse(response)
                                               .build();

    if (!client.send(ByteString.of(message.toByteArray()))) {
      throw new IOException("Write failed!");
    }
  }

  private synchronized void sendKeepAlive() throws IOException {
    if (keepAliveSender != null && client != null) {
      byte[] message = WebSocketMessage.newBuilder()
                                       .setType(WebSocketMessage.Type.REQUEST)
                                       .setRequest(WebSocketRequestMessage.newBuilder()
                                                                          .setId(System.currentTimeMillis())
                                                                          .setPath("/v1/keepalive")
                                                                          .setVerb("GET")
                                                                          .build()).build()
                                       .toByteArray();

      if (!client.send(ByteString.of(message))) {
        throw new IOException("Write failed!");
      }
    }
  }

  @Override
  public synchronized void onOpen(WebSocket webSocket, Response response) {
    if (client != null && keepAliveSender == null) {
      log("onOpen() connected");
      attempts        = 0;
      connected       = true;
      keepAliveSender = new KeepAliveSender();
      keepAliveSender.start();

      if (listener != null) {
        listener.onConnected();
      }
    }
  }

  @Override
  public synchronized void onMessage(WebSocket webSocket, ByteString payload) {
    try {
      WebSocketMessage message = WebSocketMessage.parseFrom(payload.toByteArray());

      if (message.getType().getNumber() == WebSocketMessage.Type.REQUEST_VALUE) {
        incomingRequests.add(message.getRequest());
      } else if (message.getType().getNumber() == WebSocketMessage.Type.RESPONSE_VALUE) {
        OutgoingRequest listener = outgoingRequests.remove(message.getResponse().getId());
        if (listener != null) {
          listener.onSuccess(new WebsocketResponse(message.getResponse().getStatus(),
                                                   new String(message.getResponse().getBody().toByteArray()),
                                                   message.getResponse().getHeadersList()));
        }
      }

      notifyAll();
    } catch (InvalidProtocolBufferException e) {
      warn(e);
    }
  }

  @Override
  public synchronized void onClosed(WebSocket webSocket, int code, String reason) {
    log("onClose()");
    this.connected = false;

    Iterator<Map.Entry<Long, OutgoingRequest>> iterator = outgoingRequests.entrySet().iterator();

    while (iterator.hasNext()) {
      Map.Entry<Long, OutgoingRequest> entry = iterator.next();
      entry.getValue().onError(new IOException("Closed: " + code + ", " + reason));
      iterator.remove();
    }

    if (keepAliveSender != null) {
      keepAliveSender.shutdown();
      keepAliveSender = null;
    }

    if (listener != null) {
      listener.onDisconnected();
    }

    Util.wait(this, Math.min(++attempts * 200, TimeUnit.SECONDS.toMillis(15)));

    if (client != null) {
      log("Client not null when closed, attempting to reconnect");
      client.close(1000, "OK");
      client    = null;
      connected = false;
      connect();
    }

    notifyAll();
  }

  @Override
  public synchronized void onFailure(WebSocket webSocket, Throwable t, Response response) {
    warn("onFailure()", t);

    if (response != null && (response.code() == 401 || response.code() == 403)) {
      if (listener != null) {
        listener.onAuthenticationFailure();
      }
    } else if (listener != null) {
      boolean shouldRetryConnection = listener.onGenericFailure(response, t);
      if (!shouldRetryConnection) {
        warn("Experienced a failure, and the listener indicated we should not retry the connection. Disconnecting.");
        disconnect();
      }
    }

    if (client != null) {
      onClosed(webSocket, 1000, "OK");
    }
  }

  @Override
  public void onMessage(WebSocket webSocket, String text) {
    Log.d(TAG, "onMessage(text)");
  }

  @Override
  public synchronized void onClosing(WebSocket webSocket, int code, String reason) {
    log("onClosing()");
    webSocket.close(1000, "OK");
  }

  private long elapsedTime(long startTime) {
    return System.currentTimeMillis() - startTime;
  }

  private Pair<SSLSocketFactory, X509TrustManager> createTlsSocketFactory(TrustStore trustStore) {
    try {
      SSLContext     context       = SSLContext.getInstance("TLS");
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(trustStore);
      context.init(null, trustManagers, null);

      return new Pair<>(context.getSocketFactory(), (X509TrustManager) trustManagers[0]);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private void log(String message) {
    Log.i(TAG, name + " " + message);
  }

  @SuppressWarnings("SameParameterValue")
  private void warn(String message) {
    Log.w(TAG, name + " " + message);
  }

  private void warn(Throwable e) {
    Log.w(TAG, name, e);
  }

  @SuppressWarnings("SameParameterValue")
  private void warn(String message, Throwable e) {
    Log.w(TAG, name + " " + message, e);
  }

  private class KeepAliveSender extends Thread {

    private final AtomicBoolean stop = new AtomicBoolean(false);

    public void run() {
      while (!stop.get()) {
        try {
          sleepTimer.sleep(TimeUnit.SECONDS.toMillis(KEEPALIVE_TIMEOUT_SECONDS));

          if (!stop.get()) {
            log("Sending keep alive...");
            sendKeepAlive();
          }
        } catch (Throwable e) {
          warn(e);
        }
      }
    }

    public void shutdown() {
      stop.set(true);
    }
  }

  private static class OutgoingRequest {
    private final SingleSubject<WebsocketResponse> responseSingle;

    private OutgoingRequest(SingleSubject<WebsocketResponse> future) {
      this.responseSingle = future;
    }

    public void onSuccess(WebsocketResponse response) {
      responseSingle.onSuccess(response);
    }

    public void onError(Throwable throwable) {
      responseSingle.onError(throwable);
    }
  }
}
