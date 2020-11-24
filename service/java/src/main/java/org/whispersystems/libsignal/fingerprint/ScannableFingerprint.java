/**
 * Copyright (C) 2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.fingerprint;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.fingerprint.FingerprintProtos.CombinedFingerprints;
import org.whispersystems.libsignal.fingerprint.FingerprintProtos.LogicalFingerprint;
import org.whispersystems.libsignal.util.ByteUtil;

import java.security.MessageDigest;

public class ScannableFingerprint {

  private static final int VERSION = 1;

  private final CombinedFingerprints fingerprints;

  ScannableFingerprint(byte[] localFingerprintData, byte[] remoteFingerprintData)
  {
    LogicalFingerprint localFingerprint = LogicalFingerprint.newBuilder()
                                                            .setContent(ByteString.copyFrom(ByteUtil.trim(localFingerprintData, 32)))
                                                            .build();

    LogicalFingerprint remoteFingerprint = LogicalFingerprint.newBuilder()
                                                             .setContent(ByteString.copyFrom(ByteUtil.trim(remoteFingerprintData, 32)))
                                                             .build();

    this.fingerprints = CombinedFingerprints.newBuilder()
                                            .setVersion(VERSION)
                                            .setLocalFingerprint(localFingerprint)
                                            .setRemoteFingerprint(remoteFingerprint)
                                            .build();
  }

  /**
   * @return A byte string to be displayed in a QR code.
   */
  public byte[] getSerialized() {
    return fingerprints.toByteArray();
  }

  /**
   * Compare a scanned QR code with what we expect.
   *
   * @param scannedFingerprintData The scanned data
   * @return True if matching, otehrwise false.
   * @throws FingerprintVersionMismatchException if the scanned fingerprint is the wrong version.
   */
  public boolean compareTo(byte[] scannedFingerprintData)
      throws FingerprintVersionMismatchException,
             FingerprintParsingException
  {
    try {
      CombinedFingerprints scanned = CombinedFingerprints.parseFrom(scannedFingerprintData);

      if (!scanned.hasRemoteFingerprint() || !scanned.hasLocalFingerprint() ||
          !scanned.hasVersion() || scanned.getVersion() != VERSION)
      {
        throw new FingerprintVersionMismatchException(scanned.getVersion(), VERSION);
      }

      return MessageDigest.isEqual(fingerprints.getLocalFingerprint().getContent().toByteArray(), scanned.getRemoteFingerprint().getContent().toByteArray()) &&
             MessageDigest.isEqual(fingerprints.getRemoteFingerprint().getContent().toByteArray(), scanned.getLocalFingerprint().getContent().toByteArray());
    } catch (InvalidProtocolBufferException e) {
      throw new FingerprintParsingException(e);
    }
  }
}
