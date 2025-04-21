package org.whispersystems.signalservice.internal.websocket;



import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WebsocketResponse {
  private final int                 status;
  private final String              body;
  private final Map<String, String> headers;
  private final boolean             unidentified;

  WebsocketResponse(int status, String body, List<String> headers, boolean unidentified) {
    this(status, body, parseHeaders(headers), unidentified);
  }

  WebsocketResponse(int status, String body, Map<String, String> headerMap, boolean unidentified) {
    this.status       = status;
    this.body         = body;
    this.headers      = headerMap;
    this.unidentified = unidentified;
  }

  public int getStatus() {
    return status;
  }

  public String getBody() {
    return body;
  }

  public String getHeader(String key) {
    return headers.get(Preconditions.checkNotNull(key.toLowerCase()));
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public boolean isUnidentified() {
    return unidentified;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final WebsocketResponse that = (WebsocketResponse) o;
    return status == that.status && unidentified == that.unidentified && Objects.equals(body, that.body) && Objects.equals(headers, that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, body, headers, unidentified);
  }

  private static Map<String, String> parseHeaders(List<String> rawHeaders) {
    Map<String, String> headers = new HashMap<>(rawHeaders.size());

    for (String raw : rawHeaders) {
      if (raw != null && !raw.isEmpty()) {
        int colonIndex = raw.indexOf(":");

        if (colonIndex > 0 && colonIndex < raw.length() - 1) {
          String key   = raw.substring(0, colonIndex).trim().toLowerCase();
          String value = raw.substring(colonIndex + 1).trim();

          headers.put(key, value);
        }
      }
    }

    return headers;
  }
}
