/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.libsignal.state;

import org.session.libsignal.libsignal.IdentityKeyPair;

/**
 * Provides an interface to identity information.
 *
 * @author Moxie Marlinspike
 */
public interface IdentityKeyStore {
  /**
   * Get the local client's identity key pair.
   *
   * @return The local client's persistent identity key pair.
   */
  public IdentityKeyPair getIdentityKeyPair();

}
