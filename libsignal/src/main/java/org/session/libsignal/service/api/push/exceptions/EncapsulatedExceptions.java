/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.service.api.push.exceptions;

import org.session.libsignal.service.api.crypto.UntrustedIdentityException;
import org.session.libsignal.service.api.push.exceptions.NetworkFailureException;
import org.session.libsignal.service.api.push.exceptions.UnregisteredUserException;

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
    this.untrustedIdentityExceptions = new LinkedList<UntrustedIdentityException>();
    this.unregisteredUserExceptions  = new LinkedList<UnregisteredUserException>();
    this.networkExceptions           = new LinkedList<NetworkFailureException>();

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
