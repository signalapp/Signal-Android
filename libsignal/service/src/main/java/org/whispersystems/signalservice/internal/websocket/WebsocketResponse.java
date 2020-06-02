package org.whispersystems.signalservice.internal.websocket;

public class WebsocketResponse {
  private final int    status;
  private final String body;

  WebsocketResponse(int status, String body) {
    this.status = status;
    this.body   = body;
  }

  public int getStatus() {
    return status;
  }

  public String getBody() {
    return body;
  }
}
