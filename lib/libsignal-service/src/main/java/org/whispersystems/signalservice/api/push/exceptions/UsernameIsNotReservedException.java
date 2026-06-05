package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public class UsernameIsNotReservedException extends NonSuccessfulResponseCodeException {
  public UsernameIsNotReservedException() {
    super(409, "The given username is not associated with an account.");
  }
}
