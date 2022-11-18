package org.signal.devicetransfer;

import androidx.annotation.NonNull;

/**
 * Represents the status of the transfer.
 */
public class TransferStatus {

  private final TransferMode transferMode;
  private final int          authenticationCode;

  private TransferStatus(@NonNull TransferMode transferMode) {
    this(transferMode, 0);
  }

  private TransferStatus(int authenticationCode) {
    this(TransferMode.VERIFICATION_REQUIRED, authenticationCode);
  }

  private TransferStatus(@NonNull TransferMode transferMode, int authenticationCode) {
    this.transferMode       = transferMode;
    this.authenticationCode = authenticationCode;
  }

  public @NonNull TransferMode getTransferMode() {
    return transferMode;
  }

  public int getAuthenticationCode() {
    return authenticationCode;
  }

  public static @NonNull TransferStatus ready() {
    return new TransferStatus(TransferMode.READY);
  }

  public static @NonNull TransferStatus serviceConnected() {
    return new TransferStatus(TransferMode.SERVICE_CONNECTED);
  }

  public static @NonNull TransferStatus networkConnected() {
    return new TransferStatus(TransferMode.NETWORK_CONNECTED);
  }

  public static @NonNull TransferStatus verificationRequired(@NonNull Integer authenticationCode) {
    return new TransferStatus(authenticationCode);
  }

  public static @NonNull TransferStatus startingUp() {
    return new TransferStatus(TransferMode.STARTING_UP);
  }

  public static @NonNull TransferStatus discovery() {
    return new TransferStatus(TransferMode.DISCOVERY);
  }

  public static @NonNull TransferStatus unavailable() {
    return new TransferStatus(TransferMode.UNAVAILABLE);
  }

  public static @NonNull TransferStatus shutdown() {
    return new TransferStatus(TransferMode.SHUTDOWN);
  }

  public static @NonNull TransferStatus failed() {
    return new TransferStatus(TransferMode.FAILED);
  }

  public enum TransferMode {
    UNAVAILABLE,
    FAILED,
    READY,
    STARTING_UP,
    DISCOVERY,
    NETWORK_CONNECTED,
    VERIFICATION_REQUIRED,
    SERVICE_CONNECTED,
    SERVICE_DISCONNECTED,
    SHUTDOWN
  }
}
