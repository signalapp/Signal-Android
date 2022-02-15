package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketResponseMessage;
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

/**
 * Provide a general interface to the WebSocket for making requests and reading messages sent by the server.
 * Where appropriate, it will handle retrying failed unidentified requests on the regular WebSocket.
 */
public final class SignalWebSocket {

  private static final String TAG = SignalWebSocket.class.getSimpleName();

  private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";

  private final WebSocketFactory webSocketFactory;

  private       WebSocketConnection                       webSocket;
  private final BehaviorSubject<WebSocketConnectionState> webSocketState;
  private       CompositeDisposable                       webSocketStateDisposable;

  private       WebSocketConnection                       unidentifiedWebSocket;
  private final BehaviorSubject<WebSocketConnectionState> unidentifiedWebSocketState;
  private       CompositeDisposable                       unidentifiedWebSocketStateDisposable;

  private boolean canConnect;

  public SignalWebSocket(WebSocketFactory webSocketFactory) {
    this.webSocketFactory                     = webSocketFactory;
    this.webSocketState                       = BehaviorSubject.createDefault(WebSocketConnectionState.DISCONNECTED);
    this.unidentifiedWebSocketState           = BehaviorSubject.createDefault(WebSocketConnectionState.DISCONNECTED);
    this.webSocketStateDisposable             = new CompositeDisposable();
    this.unidentifiedWebSocketStateDisposable = new CompositeDisposable();
  }

  /**
   * Get an observable stream of the identified WebSocket state. This observable is valid for the lifetime of
   * the instance, and will update as WebSocketConnections are remade.
   */
  public Observable<WebSocketConnectionState> getWebSocketState() {
    return webSocketState;
  }

  /**
   * Get an observable stream of the unidentified WebSocket state. This observable is valid for the lifetime of
   * the instance, and will update as WebSocketConnections are remade.
   */
  public Observable<WebSocketConnectionState> getUnidentifiedWebSocketState() {
    return unidentifiedWebSocketState;
  }

  /**
   * Indicate that WebSocketConnections can now be made and attempt to connect both of them.
   */
  public synchronized void connect() {
    canConnect = true;
    try {
      getWebSocket();
      getUnidentifiedWebSocket();
    } catch (WebSocketUnavailableException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Indicate that WebSocketConnections can no longer be made and disconnect both of them.
   */
  public synchronized void disconnect() {
    canConnect = false;
    disconnectIdentified();
    disconnectUnidentified();
  }

  /**
   * Indicate that the current WebSocket instances need to be destroyed and new ones should be created the
   * next time a connection is required. Intended to be used by the health monitor to cycle a WebSocket.
   */
  public synchronized void forceNewWebSockets() {
    Log.i(TAG, "Forcing new WebSockets " +
               " identified: " + (webSocket != null ? webSocket.getName() : "[null]") +
               " unidentified: " + (unidentifiedWebSocket != null ? unidentifiedWebSocket.getName() : "[null]") +
               " canConnect: " + canConnect);

    disconnectIdentified();
    disconnectUnidentified();
  }

  private void disconnectIdentified() {
    if (webSocket != null) {
      webSocketStateDisposable.dispose();

      webSocket.disconnect();
      webSocket = null;

      //noinspection ConstantConditions
      if (!webSocketState.getValue().isFailure()) {
        webSocketState.onNext(WebSocketConnectionState.DISCONNECTED);
      }
    }
  }

  private void disconnectUnidentified() {
    if (unidentifiedWebSocket != null) {
      unidentifiedWebSocketStateDisposable.dispose();

      unidentifiedWebSocket.disconnect();
      unidentifiedWebSocket = null;

      //noinspection ConstantConditions
      if (!unidentifiedWebSocketState.getValue().isFailure()) {
        unidentifiedWebSocketState.onNext(WebSocketConnectionState.DISCONNECTED);
      }
    }
  }

  private synchronized WebSocketConnection getWebSocket() throws WebSocketUnavailableException {
    if (!canConnect) {
      throw new WebSocketUnavailableException();
    }

    if (webSocket == null || webSocket.isDead()) {
      webSocketStateDisposable.dispose();

      webSocket                = webSocketFactory.createWebSocket();
      webSocketStateDisposable = new CompositeDisposable();

      Disposable state = webSocket.connect()
                                  .subscribeOn(Schedulers.computation())
                                  .observeOn(Schedulers.computation())
                                  .subscribe(webSocketState::onNext);
      webSocketStateDisposable.add(state);
    }
    return webSocket;
  }

  private synchronized WebSocketConnection getUnidentifiedWebSocket() throws WebSocketUnavailableException {
    if (!canConnect) {
      throw new WebSocketUnavailableException();
    }

    if (unidentifiedWebSocket == null || unidentifiedWebSocket.isDead()) {
      unidentifiedWebSocketStateDisposable.dispose();

      unidentifiedWebSocket                = webSocketFactory.createUnidentifiedWebSocket();
      unidentifiedWebSocketStateDisposable = new CompositeDisposable();

      Disposable state = unidentifiedWebSocket.connect()
                                              .subscribeOn(Schedulers.computation())
                                              .observeOn(Schedulers.computation())
                                              .subscribe(unidentifiedWebSocketState::onNext);
      unidentifiedWebSocketStateDisposable.add(state);
    }
    return unidentifiedWebSocket;
  }

  /**
   * Send keep-alive messages over both WebSocketConnections.
   */
  public synchronized void sendKeepAlive() throws IOException {
    if (canConnect) {
      try {
        getWebSocket().sendKeepAlive();
        getUnidentifiedWebSocket().sendKeepAlive();
      } catch (WebSocketUnavailableException e) {
        throw new AssertionError(e);
      }
    }
  }

  public Single<WebsocketResponse> request(WebSocketRequestMessage requestMessage) {
    try {
      return getWebSocket().sendRequest(requestMessage);
    } catch (IOException e) {
      return Single.error(e);
    }
  }

  public Single<WebsocketResponse> request(WebSocketRequestMessage requestMessage, Optional<UnidentifiedAccess> unidentifiedAccess) {
    if (unidentifiedAccess.isPresent()) {
      WebSocketRequestMessage message = WebSocketRequestMessage.newBuilder(requestMessage)
                                                               .addHeaders("Unidentified-Access-Key:" + Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey()))
                                                               .build();
      try {
        return getUnidentifiedWebSocket().sendRequest(message)
                                         .flatMap(r -> {
                                           if (r.getStatus() == 401) {
                                             return request(requestMessage);
                                           }
                                           return Single.just(r);
                                         })
                                         .onErrorResumeNext(t -> request(requestMessage));
      } catch (IOException e) {
        return Single.error(e);
      }
    } else {
      return request(requestMessage);
    }
  }

  /**
   * <p>
   * A blocking call that reads a message off the pipe. When this call returns, the message has been
   * acknowledged and will not be retransmitted. This will return {@link Optional#absent()} when an
   * empty response is hit, which indicates the WebSocket is empty.
   * <p>
   * You can specify a {@link MessageReceivedCallback} that will be called before the received message is acknowledged.
   * This allows you to write the received message to durable storage before acknowledging receipt of it to the
   * server.
   * <p>
   * Important: The empty response will only be hit once for each connection. That means if you get
   * an empty response and call readOrEmpty() again on the same instance, you will not get an empty
   * response, and instead will block until you get an actual message. This will, however, reset if
   * connection breaks (if, for instance, you lose and regain network).
   *
   * @param timeout  The timeout to wait for.
   * @param callback A callback that will be called before the message receipt is acknowledged to the server.
   * @return The message read (same as the message sent through the callback).
   */
  @SuppressWarnings("DuplicateThrows")
  public Optional<SignalServiceEnvelope> readOrEmpty(long timeout, MessageReceivedCallback callback)
      throws TimeoutException, WebSocketUnavailableException, IOException
  {
    while (true) {
      WebSocketRequestMessage  request  = getWebSocket().readRequest(timeout);
      WebSocketResponseMessage response = createWebSocketResponse(request);
      try {
        if (isSignalServiceEnvelope(request)) {
          Optional<String> timestampHeader = findHeader(request);
          long             timestamp       = 0;

          if (timestampHeader.isPresent()) {
            try {
              timestamp = Long.parseLong(timestampHeader.get());
            } catch (NumberFormatException e) {
              Log.w(TAG, "Failed to parse " + SERVER_DELIVERED_TIMESTAMP_HEADER);
            }
          }

          SignalServiceEnvelope envelope = new SignalServiceEnvelope(request.getBody().toByteArray(), timestamp);

          callback.onMessage(envelope);
          return Optional.of(envelope);
        } else if (isSocketEmptyRequest(request)) {
          return Optional.absent();
        }
      } finally {
        getWebSocket().sendResponse(response);
      }
    }
  }

  private static boolean isSignalServiceEnvelope(WebSocketRequestMessage message) {
    return "PUT".equals(message.getVerb()) && "/api/v1/message".equals(message.getPath());
  }

  private static boolean isSocketEmptyRequest(WebSocketRequestMessage message) {
    return "PUT".equals(message.getVerb()) && "/api/v1/queue/empty".equals(message.getPath());
  }

  private static WebSocketResponseMessage createWebSocketResponse(WebSocketRequestMessage request) {
    if (isSignalServiceEnvelope(request)) {
      return WebSocketResponseMessage.newBuilder()
                                     .setId(request.getId())
                                     .setStatus(200)
                                     .setMessage("OK")
                                     .build();
    } else {
      return WebSocketResponseMessage.newBuilder()
                                     .setId(request.getId())
                                     .setStatus(400)
                                     .setMessage("Unknown")
                                     .build();
    }
  }

  private static Optional<String> findHeader(WebSocketRequestMessage message) {
    if (message.getHeadersCount() == 0) {
      return Optional.absent();
    }

    for (String header : message.getHeadersList()) {
      if (header.startsWith(SERVER_DELIVERED_TIMESTAMP_HEADER)) {
        String[] split = header.split(":");
        if (split.length == 2 && split[0].trim().toLowerCase().equals(SERVER_DELIVERED_TIMESTAMP_HEADER.toLowerCase())) {
          return Optional.of(split[1].trim());
        }
      }
    }

    return Optional.absent();
  }

  /**
   * For receiving a callback when a new message has been
   * received.
   */
  public interface MessageReceivedCallback {
    void onMessage(SignalServiceEnvelope envelope);
  }
}
