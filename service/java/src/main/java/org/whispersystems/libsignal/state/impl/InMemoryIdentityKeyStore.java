/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.state.impl;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;

import java.util.HashMap;
import java.util.Map;

public class InMemoryIdentityKeyStore implements IdentityKeyStore {

  private final Map<SignalProtocolAddress, IdentityKey> trustedKeys = new HashMap<SignalProtocolAddress, IdentityKey>();

  private final IdentityKeyPair identityKeyPair;
  private final int             localRegistrationId;

  public InMemoryIdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId) {
    this.identityKeyPair     = identityKeyPair;
    this.localRegistrationId = localRegistrationId;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeyPair;
  }

  @Override
  public int getLocalRegistrationId() {
    return localRegistrationId;
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    IdentityKey existing = trustedKeys.get(address);

    if (!identityKey.equals(existing)) {
      trustedKeys.put(address, identityKey);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    IdentityKey trusted = trustedKeys.get(address);
    return (trusted == null || trusted.equals(identityKey));
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    return trustedKeys.get(address);
  }
}
