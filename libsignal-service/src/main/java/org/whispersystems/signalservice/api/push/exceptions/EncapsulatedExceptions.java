/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.push.exceptions;

import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

import java.util.LinkedList;
import java.util.List;

public class EncapsulatedExceptions extends Throwable {

  private final List<UntrustedIdentityException> untrustedIdentityExceptions;
  private final List<UnregisteredUserException>  unregisteredUserExceptions;
  private final List<NetworkFailureException>    networkExceptions;

  public EncapsulatedExceptions(List<UntrustedIdentityException> untrustedIdentities,
                                List<UnregisteredUserException> unregisteredUsers,
                                List<NetworkFailureException> networkExceptions)
  {
    this.untrustedIdentityExceptions = untrustedIdentities;
    this.unregisteredUserExceptions  = unregisteredUsers;
    this.networkExceptions           = networkExceptions;
  }

  public EncapsulatedExceptions(UntrustedIdentityException e) {
    this.untrustedIdentityExceptions = new LinkedList<>();
    this.unregisteredUserExceptions  = new LinkedList<>();
    this.networkExceptions           = new LinkedList<>();

    this.untrustedIdentityExceptions.add(e);
  }

  public List<UntrustedIdentityException> getUntrustedIdentityExceptions() {
    return untrustedIdentityExceptions;
  }

  public List<UnregisteredUserException> getUnregisteredUserExceptions() {
    return unregisteredUserExceptions;
  }

  public List<NetworkFailureException> getNetworkExceptions() {
    return networkExceptions;
  }
}
