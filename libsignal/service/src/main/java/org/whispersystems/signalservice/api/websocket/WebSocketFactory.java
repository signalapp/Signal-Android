package org.whispersystems.signalservice.api.websocket;

import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

public interface WebSocketFactory {
  WebSocketConnection createWebSocket();
  WebSocketConnection createUnidentifiedWebSocket();
}
