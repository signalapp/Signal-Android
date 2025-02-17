package org.whispersystems.signalservice.api.websocket

/**
 * Callbacks to provide WebSocket health information to a monitor.
 */
interface HealthMonitor {
  fun onKeepAliveResponse(sentTimestamp: Long, isIdentifiedWebSocket: Boolean)

  fun onMessageError(status: Int, isIdentifiedWebSocket: Boolean)
}
