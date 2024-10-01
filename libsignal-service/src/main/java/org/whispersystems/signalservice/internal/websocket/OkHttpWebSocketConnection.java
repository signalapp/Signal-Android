package org.whispersystems.signalservice.internal.websocket;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory;
import org.whispersystems.signalservice.api.websocket.HealthMonitor;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.SingleSubject;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class OkHttpWebSocketConnection extends WebSocketListener implements WebSocketConnection {

  private static final String TAG                         = OkHttpWebSocketConnection.class.getSimpleName();
  public static final  int    KEEPALIVE_FREQUENCY_SECONDS = 30;

  private final LinkedList<WebSocketRequestMessage> incomingRequests = new LinkedList<>();
  private final Map<Long, OutgoingRequest>          outgoingRequests = new HashMap<>();
  private final Set<Long>                           keepAlives       = new HashSet<>();

  private final String                                    name;
  private final TrustStore                                trustStore;
  private final Optional<CredentialsProvider>             credentialsProvider;
  private final String                                    signalAgent;
  private final HealthMonitor                             healthMonitor;
  private final List<Interceptor>                         interceptors;
  private final Optional<Dns>                             dns;
  private final Optional<SignalProxy>                     signalProxy;
  private final BehaviorSubject<WebSocketConnectionState> webSocketState;
  private final boolean                                   allowStories;
  private final SignalServiceUrl[]                        serviceUrls;
  private final String                                    extraPathUri;
  private final SecureRandom                              random;

  private WebSocket client;

  public OkHttpWebSocketConnection(String name,
                                   SignalServiceConfiguration serviceConfiguration,
                                   Optional<CredentialsProvider> credentialsProvider,
                                   String signalAgent,
                                   HealthMonitor healthMonitor,
                                   boolean allowStories) {
    this(name, serviceConfiguration, credentialsProvider, signalAgent, healthMonitor, "", allowStories);
  }

  public OkHttpWebSocketConnection(String name,
                                   SignalServiceConfiguration serviceConfiguration,
                                   Optional<CredentialsProvider> credentialsProvider,
                                   String signalAgent,
                                   HealthMonitor healthMonitor,
                                   String extraPathUri,
                                   boolean allowStories)
  {
    this.name                = "[" + name + ":" + System.identityHashCode(this) + "]";
    this.trustStore          = serviceConfiguration.getSignalServiceUrls()[0].getTrustStore();
    this.credentialsProvider = credentialsProvider;
    this.signalAgent         = signalAgent;
    this.interceptors        = serviceConfiguration.getNetworkInterceptors();
    this.dns                 = serviceConfiguration.getDns();
    this.signalProxy         = serviceConfiguration.getSignalProxy();
    this.healthMonitor       = healthMonitor;
    this.webSocketState      = BehaviorSubject.createDefault(WebSocketConnectionState.DISCONNECTED);
    this.allowStories        = allowStories;
    this.serviceUrls         = serviceConfiguration.getSignalServiceUrls();
    this.extraPathUri        = extraPathUri;
    this.random              = new SecureRandom();
  }

  @Override
  public String getName() {
    return name;
  }

  private Pair<SignalServiceUrl, String> getConnectionInfo() {
    SignalServiceUrl serviceUrl = serviceUrls[random.nextInt(serviceUrls.length)];
    String           uri        = serviceUrl.getUrl().replace("https://", "wss://").replace("http://", "ws://");

    return new Pair<>(serviceUrl, uri + "/v1/websocket/" + extraPathUri);
  }

  @Override
  public synchronized Observable<WebSocketConnectionState> connect() {
    log("connect()");

    if (client == null) {
      Pair<SignalServiceUrl, String> connectionInfo = getConnectionInfo();
      SignalServiceUrl               serviceUrl     = connectionInfo.first();
      String                         wsUri          = connectionInfo.second();

      Pair<SSLSocketFactory, X509TrustManager> socketFactory = createTlsSocketFactory(trustStore);

      OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder().sslSocketFactory(new Tls12SocketFactory(socketFactory.first()),
                                                                                       socketFactory.second())
                                                                     .connectionSpecs(serviceUrl.getConnectionSpecs().orElse(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
                                                                     .readTimeout(KEEPALIVE_FREQUENCY_SECONDS + 10, TimeUnit.SECONDS)
                                                                     .dns(dns.orElse(Dns.SYSTEM))
                                                                     .connectTimeout(KEEPALIVE_FREQUENCY_SECONDS + 10, TimeUnit.SECONDS);

      for (Interceptor interceptor : interceptors) {
        clientBuilder.addInterceptor(interceptor);
      }

      if (signalProxy.isPresent()) {
        clientBuilder.socketFactory(new TlsProxySocketFactory(signalProxy.get().getHost(), signalProxy.get().getPort(), dns));
      }

      OkHttpClient okHttpClient = clientBuilder.build();

      Request.Builder requestBuilder = new Request.Builder().url(wsUri);

      if (signalAgent != null) {
        requestBuilder.addHeader("X-Signal-Agent", signalAgent);
      }

      if (credentialsProvider.isPresent()) {
        if (credentialsProvider.get().getUsername() != null && credentialsProvider.get().getPassword() != null) {
          requestBuilder.addHeader("Authorization", Credentials.basic(credentialsProvider.get().getUsername(), credentialsProvider.get().getPassword()));
        } else {
          Log.w(TAG, "CredentialsProvider was present, but username or password was missing!");
        }
      }

      requestBuilder.addHeader("X-Signal-Receive-Stories", allowStories ? "true" : "false");

      if (serviceUrl.getHostHeader().isPresent()) {
        requestBuilder.addHeader("Host", serviceUrl.getHostHeader().get());
        Log.w(TAG, "Using alternate host: " + serviceUrl.getHostHeader().get());
      }

      webSocketState.onNext(WebSocketConnectionState.CONNECTING);

      this.client = okHttpClient.newWebSocket(requestBuilder.build(), this);
    }
    return webSocketState;
  }

  @Override
  public synchronized boolean isDead() {
    return client == null;
  }

  @Override
  public synchronized void disconnect() {
    log("disconnect()");

    if (client != null) {
      client.close(1000, "OK");
      client = null;
      webSocketState.onNext(WebSocketConnectionState.DISCONNECTING);
    }

    notifyAll();
  }

  @Override
  public synchronized Optional<WebSocketRequestMessage> readRequestIfAvailable() {
    if (incomingRequests.size() > 0) {
      return Optional.of(incomingRequests.removeFirst());
    } else {
      return Optional.empty();
    }
  }

  @Override
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

  @Override
  public synchronized Single<WebsocketResponse> sendRequest(WebSocketRequestMessage request) throws IOException {
    if (client == null) {
      throw new IOException("No connection!");
    }

    WebSocketMessage message = new WebSocketMessage.Builder()
                                                   .type(WebSocketMessage.Type.REQUEST)
                                                   .request(request)
                                                   .build();

    SingleSubject<WebsocketResponse> single = SingleSubject.create();

    outgoingRequests.put(request.id, new OutgoingRequest(single));

    if (!client.send(ByteString.of(message.encode()))) {
      throw new IOException("Write failed!");
    }

    return single.subscribeOn(Schedulers.io())
                 .observeOn(Schedulers.io())
                 .timeout(10, TimeUnit.SECONDS, Schedulers.io());
  }

  @Override
  public synchronized void sendResponse(WebSocketResponseMessage response) throws IOException {
    if (client == null) {
      throw new IOException("Connection closed!");
    }

    WebSocketMessage message = new WebSocketMessage.Builder()
                                                   .type(WebSocketMessage.Type.RESPONSE)
                                                   .response(response)
                                                   .build();

    if (!client.send(ByteString.of(message.encode()))) {
      throw new IOException("Write failed!");
    }
  }

  @Override
  public synchronized void sendKeepAlive() throws IOException {
    if (client != null) {
      log("Sending keep alive...");
      long id = System.currentTimeMillis();
      byte[] message = new WebSocketMessage.Builder()
                                           .type(WebSocketMessage.Type.REQUEST)
                                           .request(new WebSocketRequestMessage.Builder()
                                                                               .id(id)
                                                                               .path("/v1/keepalive")
                                                                               .verb("GET")
                                                                               .build())
                                           .build()
                                           .encode();
      keepAlives.add(id);
      if (!client.send(ByteString.of(message))) {
        throw new IOException("Write failed!");
      }
    }
  }

  @Override
  public synchronized void onOpen(WebSocket webSocket, Response response) {
    if (client != null) {
      log("onOpen() connected");
      webSocketState.onNext(WebSocketConnectionState.CONNECTED);
    }
  }

  @Override
  public synchronized void onMessage(WebSocket webSocket, ByteString payload) {
    try {
      WebSocketMessage message = WebSocketMessage.ADAPTER.decode(payload.toByteArray());

      if (message.type == WebSocketMessage.Type.REQUEST) {
        incomingRequests.add(message.request);
      } else if (message.type == WebSocketMessage.Type.RESPONSE) {
        OutgoingRequest listener = outgoingRequests.remove(message.response.id);
        if (listener != null) {
          listener.onSuccess(new WebsocketResponse(message.response.status,
                                                   message.response.body == null ? "" : new String(message.response.body.toByteArray()),
                                                   message.response.headers,
                                                   !credentialsProvider.isPresent()));
          if (message.response.status >= 400) {
            healthMonitor.onMessageError(message.response.status, credentialsProvider.isPresent());
          }
        } else if (keepAlives.remove(message.response.id)) {
          healthMonitor.onKeepAliveResponse(message.response.id, credentialsProvider.isPresent());
        }
      }

      notifyAll();
    } catch (IOException e) {
      warn(e);
    }
  }

  @Override
  public synchronized void onClosed(WebSocket webSocket, int code, String reason) {
    log("onClose()");
    webSocketState.onNext(WebSocketConnectionState.DISCONNECTED);

    cleanupAfterShutdown();

    notifyAll();
  }

  @Override
  public synchronized void onFailure(WebSocket webSocket, Throwable t, Response response) {
    warn("onFailure()", t);

    if (response != null && (response.code() == 401 || response.code() == 403)) {
      webSocketState.onNext(WebSocketConnectionState.AUTHENTICATION_FAILED);
    } else {
      webSocketState.onNext(WebSocketConnectionState.FAILED);
    }

    cleanupAfterShutdown();

    notifyAll();
  }

  private void cleanupAfterShutdown() {
    Iterator<Map.Entry<Long, OutgoingRequest>> iterator = outgoingRequests.entrySet().iterator();

    while (iterator.hasNext()) {
      Map.Entry<Long, OutgoingRequest> entry = iterator.next();
      entry.getValue().onError(new IOException("Closed unexpectedly"));
      iterator.remove();
    }

    if (client != null) {
      log("Client not null when closed");
      client.close(1000, "OK");
      client = null;
    }
  }

  @Override
  public void onMessage(WebSocket webSocket, String text) {
    Log.d(TAG, "onMessage(text)");
  }

  @Override
  public synchronized void onClosing(WebSocket webSocket, int code, String reason) {
    log("onClosing()");
    webSocketState.onNext(WebSocketConnectionState.DISCONNECTING);
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
