package org.whispersystems.signalservice.api;

import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.EnvelopeResponse;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketResponseMessage;
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                                         });
      } catch (IOException e) {
        return Single.error(e);
      }
    } else {
      return request(requestMessage);
    }
  }

  /**
   * The reads a batch of messages off of the websocket.
   *
   * Rather than just provide you the batch as a return value, it will invoke the provided callback with the
   * batch as an argument. If you are able to successfully process them, this method will then ack all of the
   * messages so that they won't be re-delivered in the future.
   *
   * The return value of this method is a boolean indicating whether or not there are more messages in the
   * queue to be read (true if there's still more, or false if you've drained everything).
   *
   * However, this return value is only really useful the first time you read from the websocket. That's because
   * the websocket will only ever let you know if it's drained *once* for any given connection. So if this method
   * returns false, a subsequent call while using the same websocket connection will simply block until we either
   * get a new message or hit the timeout.
   *
   * Concerning the requested batch size, it's worth noting that this is simply an upper bound. This method will
   * not wait extra time until the batch has "filled up". Instead, it will wait for a single message, and then
   * take any extra messages that are also available up until you've hit your batch size.
   */
  @SuppressWarnings("DuplicateThrows")
  public boolean readMessageBatch(long timeout, int batchSize, MessageReceivedCallback callback)
      throws TimeoutException, WebSocketUnavailableException, IOException
  {
    List<EnvelopeResponse> responses     = new ArrayList<>();
    boolean                hitEndOfQueue = false;

    Optional<EnvelopeResponse> firstEnvelope = waitForSingleMessage(timeout);

    if (firstEnvelope.isPresent()) {
      responses.add(firstEnvelope.get());
    } else {
      hitEndOfQueue = true;
    }

    if (!hitEndOfQueue) {
      for (int i = 1; i < batchSize; i++) {
        Optional<WebSocketRequestMessage> request = getWebSocket().readRequestIfAvailable();

        if (request.isPresent()) {
          if (isSignalServiceEnvelope(request.get())) {
            responses.add(requestToEnvelopeResponse(request.get()));
          } else if (isSocketEmptyRequest(request.get())) {
            hitEndOfQueue = true;
            break;
          }
        } else {
          break;
        }
      }
    }

    if (responses.size() > 0) {
      boolean successfullyProcessed = false;

      try {
        successfullyProcessed = callback.onMessageBatch(responses);
      } finally {
        if (successfullyProcessed) {
          for (EnvelopeResponse response : responses) {
            getWebSocket().sendResponse(createWebSocketResponse(response.getWebsocketRequest()));
          }
        }
      }
    }

    return !hitEndOfQueue;
  }

  @SuppressWarnings("DuplicateThrows")
  private Optional<EnvelopeResponse> waitForSingleMessage(long timeout)
      throws TimeoutException, WebSocketUnavailableException, IOException
  {
    while (true) {
      WebSocketRequestMessage request = getWebSocket().readRequest(timeout);

      if (isSignalServiceEnvelope(request)) {
        return Optional.of(requestToEnvelopeResponse(request));
      } else if (isSocketEmptyRequest(request)) {
        return Optional.empty();
      }
    }
  }

  private static EnvelopeResponse requestToEnvelopeResponse(WebSocketRequestMessage request)
      throws InvalidProtocolBufferException
  {
    Optional<String> timestampHeader = findHeader(request);
    long             timestamp       = 0;

    if (timestampHeader.isPresent()) {
      try {
        timestamp = Long.parseLong(timestampHeader.get());
      } catch (NumberFormatException e) {
        Log.w(TAG, "Failed to parse " + SERVER_DELIVERED_TIMESTAMP_HEADER);
      }
    }

    SignalServiceProtos.Envelope envelope = SignalServiceProtos.Envelope.parseFrom(request.getBody().toByteArray());

    return new EnvelopeResponse(envelope, timestamp, request);
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
      return Optional.empty();
    }

    for (String header : message.getHeadersList()) {
      if (header.startsWith(SERVER_DELIVERED_TIMESTAMP_HEADER)) {
        String[] split = header.split(":");
        if (split.length == 2 && split[0].trim().toLowerCase().equals(SERVER_DELIVERED_TIMESTAMP_HEADER.toLowerCase())) {
          return Optional.of(split[1].trim());
        }
      }
    }

    return Optional.empty();
  }

  /**
   * For receiving a callback when a new message has been
   * received.
   */
  public interface MessageReceivedCallback {

    /** True if you successfully processed the message, otherwise false. **/
    boolean onMessageBatch(List<EnvelopeResponse> envelopeResponses);
  }
}
