package org.whispersystems.signalservice.api.websocket;

import java.io.IOException;

/**
 * Thrown when the WebSocket is not available for use by runtime policy. Currently, the
 * WebSocket is only unavailable when networking is blocked by a device transfer or if
 * requesting to connect via auth but provide no auth credentials.
 */
public final class WebSocketUnavailableException extends IOException {
  public WebSocketUnavailableException() {
    super("WebSocket not currently available.");
  }

  public WebSocketUnavailableException(String reason) {
    super("WebSocket not currently available. Reason: " + reason);
  }
}
