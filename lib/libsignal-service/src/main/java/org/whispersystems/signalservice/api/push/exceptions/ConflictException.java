package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

/**
 * Represents a 409 http conflict error.
 */
public class ConflictException extends NonSuccessfulResponseCodeException {
  public ConflictException() {
    super(409, "Conflict");
  }
}
