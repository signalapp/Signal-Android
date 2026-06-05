package org.whispersystems.signalservice.internal.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public final class NotInGroupException extends NonSuccessfulResponseCodeException {
  public NotInGroupException() {
    super(403);
  }
}
