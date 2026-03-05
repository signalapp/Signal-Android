package org.signal.devicetransfer;

import androidx.annotation.NonNull;

/**
 * Represents the various type of failure with creating a WiFi Direction connection.
 */
final class WifiDirectUnavailableException extends Exception {

  private final Reason reason;

  public WifiDirectUnavailableException(@NonNull Reason reason) {
    this.reason = reason;
  }

  public @NonNull Reason getReason() {
    return reason;
  }

  public enum Reason {
    WIFI_P2P_MANAGER,
    CHANNEL_INITIALIZATION,
    SERVICE_DISCOVERY_START,
    SERVICE_START,
    SERVICE_CONNECT_FAILURE,
    SERVICE_CREATE_GROUP,
    SERVICE_NOT_INITIALIZED
  }
}
