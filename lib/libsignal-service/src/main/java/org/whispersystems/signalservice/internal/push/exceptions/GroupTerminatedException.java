package org.whispersystems.signalservice.internal.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public final class GroupTerminatedException extends NonSuccessfulResponseCodeException {
  public GroupTerminatedException() {
    super(423);
  }
}
