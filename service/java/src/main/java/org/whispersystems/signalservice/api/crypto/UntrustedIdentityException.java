/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.crypto;

import org.whispersystems.libsignal.IdentityKey;

public class UntrustedIdentityException extends Exception {

  private final IdentityKey identityKey;
  private final String      e164number;

  public UntrustedIdentityException(String s, String e164number, IdentityKey identityKey) {
    super(s);
    this.e164number  = e164number;
    this.identityKey = identityKey;
  }

  public UntrustedIdentityException(UntrustedIdentityException e) {
    this(e.getMessage(), e.getE164Number(), e.getIdentityKey());
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public String getE164Number() {
    return e164number;
  }

}
