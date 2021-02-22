/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.api.crypto;

import org.session.libsignal.libsignal.IdentityKey;

public class UntrustedIdentityException extends Exception {

  private final IdentityKey identityKey;
  private final String      e164number;

  public UntrustedIdentityException(String s, String e164number, IdentityKey identityKey) {
    super(s);
    this.e164number  = e164number;
    this.identityKey = identityKey;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public String getE164Number() {
    return e164number;
  }

}
