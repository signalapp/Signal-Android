package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public class UsernameTakenException extends NonSuccessfulResponseCodeException {
  public UsernameTakenException() {
    super(409);
  }
}
