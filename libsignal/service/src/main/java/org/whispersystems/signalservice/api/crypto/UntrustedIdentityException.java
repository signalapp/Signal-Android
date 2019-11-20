/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.crypto;

import org.whispersystems.libsignal.IdentityKey;

public class UntrustedIdentityException extends Exception {

  private final IdentityKey identityKey;
  private final String      identifier;

  public UntrustedIdentityException(String s, String identifier, IdentityKey identityKey) {
    super(s);
    this.identifier  = identifier;
    this.identityKey = identityKey;
  }

  public UntrustedIdentityException(UntrustedIdentityException e) {
    this(e.getMessage(), e.getIdentifier(), e.getIdentityKey());
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public String getIdentifier() {
    return identifier;
  }

}
