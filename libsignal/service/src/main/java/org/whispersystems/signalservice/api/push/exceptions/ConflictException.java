package org.whispersystems.signalservice.api.push.exceptions;

/**
 * Represents a 409 http conflict error.
 */
public class ConflictException extends NonSuccessfulResponseCodeException {
  public ConflictException() {
    super("Conflict");
  }
}
