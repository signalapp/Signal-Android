package org.signal.devicetransfer;

import androidx.annotation.NonNull;

/**
 * Thrown when there's an issue generating the self-signed certificates for TLS.
 */
final class KeyGenerationFailedException extends Throwable {
  public KeyGenerationFailedException(@NonNull Exception e) {
    super(e);
  }
}
