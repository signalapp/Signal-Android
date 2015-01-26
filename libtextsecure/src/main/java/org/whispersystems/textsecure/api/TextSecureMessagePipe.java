package org.whispersystems.textsecure.api;

import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.util.CredentialsProvider;
import org.whispersystems.textsecure.internal.websocket.WebSocketConnection;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketResponseMessage;

public class TextSecureMessagePipe {

  private final WebSocketConnection websocket;
  private final CredentialsProvider credentialsProvider;

  public TextSecureMessagePipe(WebSocketConnection websocket, CredentialsProvider credentialsProvider) {
    this.websocket           = websocket;
    this.credentialsProvider = credentialsProvider;

    this.websocket.connect();
  }

  public TextSecureEnvelope read(long timeout, TimeUnit unit)
      throws InvalidVersionException, IOException, TimeoutException
  {
    return read(timeout, unit, new NullMessagePipeCallback());
  }

  public TextSecureEnvelope read(long timeout, TimeUnit unit, MessagePipeCallback callback)
      throws TimeoutException, IOException, InvalidVersionException
  {
    while (true) {
      WebSocketRequestMessage  request  = websocket.readRequest(unit.toMillis(timeout));
      WebSocketResponseMessage response = createWebSocketResponse(request);

      try {
        if (isTextSecureEnvelope(request)) {
          TextSecureEnvelope envelope = new TextSecureEnvelope(request.getBody().toByteArray(),
                                                               credentialsProvider.getSignalingKey());

          callback.onMessage(envelope);
          return envelope;
        }
      } finally {
        websocket.sendResponse(response);
      }
    }
  }

  public void shutdown() throws IOException {
    websocket.disconnect();
  }

  private boolean isTextSecureEnvelope(WebSocketRequestMessage message) {
    return "PUT".equals(message.getVerb()) && "/api/v1/message".equals(message.getPath());
  }

  private WebSocketResponseMessage createWebSocketResponse(WebSocketRequestMessage request) {
    if (isTextSecureEnvelope(request)) {
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

  public static interface MessagePipeCallback {
    public void onMessage(TextSecureEnvelope envelope);
  }

  private static class NullMessagePipeCallback implements MessagePipeCallback {
    @Override
    public void onMessage(TextSecureEnvelope envelope) {}
  }

}
