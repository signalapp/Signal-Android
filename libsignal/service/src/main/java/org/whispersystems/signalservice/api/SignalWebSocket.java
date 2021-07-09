package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketResponseMessage;
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.reactivex.rxjava3.core.Single;

/**
 * Provide a general interface to the WebSocket for making requests and reading messages sent by the server.
 * Where appropriate, it will handle retrying failed unidentified requests on the regular WebSocket.
 */
public final class SignalWebSocket {

  private static final String TAG = SignalWebSocket.class.getSimpleName();

  private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";

  private final WebSocketFactory webSocketFactory;

  private WebSocketConnection webSocket;
  private WebSocketConnection unidentifiedWebSocket;
  private boolean             canConnect;

  public SignalWebSocket(WebSocketFactory webSocketFactory) {
    this.webSocketFactory = webSocketFactory;
  }

  public synchronized void connect() {
    canConnect = true;
    try {
      getWebSocket();
      getUnidentifiedWebSocket();
    } catch (WebSocketUnavailableException e) {
      throw new AssertionError(e);
    }
  }

  public synchronized void disconnect() {
    canConnect = false;

    if (webSocket != null) {
      webSocket.disconnect();
      webSocket = null;
    }

    if (unidentifiedWebSocket != null) {
      unidentifiedWebSocket.disconnect();
      unidentifiedWebSocket = null;
    }
  }

  private synchronized WebSocketConnection getWebSocket() throws WebSocketUnavailableException {
    if (!canConnect) {
      throw new WebSocketUnavailableException();
    }

    if (webSocket == null || webSocket.isDead()) {
      webSocket = webSocketFactory.createWebSocket();
      webSocket.connect();
    }
    return webSocket;
  }

  private synchronized WebSocketConnection getUnidentifiedWebSocket() throws WebSocketUnavailableException {
    if (!canConnect) {
      throw new WebSocketUnavailableException();
    }

    if (unidentifiedWebSocket == null || unidentifiedWebSocket.isDead()) {
      unidentifiedWebSocket = webSocketFactory.createUnidentifiedWebSocket();
      unidentifiedWebSocket.connect();
    }
    return unidentifiedWebSocket;
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
      Single<WebsocketResponse> response;
      try {
        response = getUnidentifiedWebSocket().sendRequest(message);
      } catch (IOException e) {
        return Single.error(e);
      }

      return response.flatMap(r -> {
                       if (r.getStatus() == 401) {
                         return request(requestMessage);
                       }
                       return Single.just(r);
                     })
                     .onErrorResumeNext(t -> request(requestMessage));
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
