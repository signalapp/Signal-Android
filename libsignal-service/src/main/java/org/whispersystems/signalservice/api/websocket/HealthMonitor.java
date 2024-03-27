package org.whispersystems.signalservice.api.websocket;

/**
 * Callbacks to provide WebSocket health information to a monitor.
 */
public interface HealthMonitor {
  void onKeepAliveResponse(long sentTimestamp, boolean isIdentifiedWebSocket, boolean keepMonitoring);

  void onMessageError(int status, boolean isIdentifiedWebSocket);
}
