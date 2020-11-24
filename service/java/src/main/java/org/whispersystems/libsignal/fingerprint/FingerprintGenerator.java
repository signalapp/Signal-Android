/**
 * Copyright (C) 2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.fingerprint;

import org.whispersystems.libsignal.IdentityKey;

import java.util.List;

public interface FingerprintGenerator {
  public Fingerprint createFor(String localStableIdentifier, IdentityKey localIdentityKey,
                               String remoteStableIdentifier, IdentityKey remoteIdentityKey);

  public Fingerprint createFor(String localStableIdentifier, List<IdentityKey> localIdentityKey,
                               String remoteStableIdentifier, List<IdentityKey> remoteIdentityKey);
}
