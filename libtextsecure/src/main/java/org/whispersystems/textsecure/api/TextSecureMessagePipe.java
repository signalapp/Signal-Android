package org.whispersystems.textsecure.api;

import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.util.CredentialsProvider;
import org.whispersystems.textsecure.internal.websocket.WebSocketConnection;
import org.whispersystems.textsecure.internal.websocket.WebSocketProtos;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketRequestMessage;

public class TextSecureMessagePipe {

  private final WebSocketConnection websocket;
  private final CredentialsProvider credentialsProvider;

  public TextSecureMessagePipe(WebSocketConnection websocket, CredentialsProvider credentialsProvider) {
    this.websocket           = websocket;
    this.credentialsProvider = credentialsProvider;

    this.websocket.connect();
  }

  public TextSecureEnvelope read(long timeout, TimeUnit unit)
      throws TimeoutException, IOException, InvalidVersionException
  {
    while (true) {
      WebSocketRequestMessage request = websocket.readRequest(unit.toMillis(timeout));

      if (isTextSecureEnvelope(request)) {
        websocket.sendResponse(WebSocketProtos.WebSocketResponseMessage.newBuilder()
                                                                       .setId(request.getId())
                                                                       .setStatus(200)
                                                                       .setMessage("OK")
                                                                       .build());

        return new TextSecureEnvelope(request.getBody().toByteArray(), credentialsProvider.getSignalingKey());
      }
    }
  }

  public void shutdown() throws IOException {
    websocket.disconnect();
  }

  private boolean isTextSecureEnvelope(WebSocketRequestMessage message) {
    return "PUT".equals(message.getVerb()) && "/api/v1/message".equals(message.getPath());
  }

}
