/**
 * Copyright (C) 2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.fingerprint;

public class FingerprintIdentifierMismatchException extends Exception {

  private final String localIdentifier;
  private final String remoteIdentifier;
  private final String scannedLocalIdentifier;
  private final String scannedRemoteIdentifier;

  public FingerprintIdentifierMismatchException(String localIdentifier, String remoteIdentifier,
                                                String scannedLocalIdentifier, String scannedRemoteIdentifier)
  {
    this.localIdentifier         = localIdentifier;
    this.remoteIdentifier        = remoteIdentifier;
    this.scannedLocalIdentifier  = scannedLocalIdentifier;
    this.scannedRemoteIdentifier = scannedRemoteIdentifier;
  }

  public String getScannedRemoteIdentifier() {
    return scannedRemoteIdentifier;
  }

  public String getScannedLocalIdentifier() {
    return scannedLocalIdentifier;
  }

  public String getRemoteIdentifier() {
    return remoteIdentifier;
  }

  public String getLocalIdentifier() {
    return localIdentifier;
  }
}
