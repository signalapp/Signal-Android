package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.zkgroup.InvalidInputException;

/**
 * Thrown when a group has some data that cannot be decrypted, or is in some other way in an
 * unexpected state.
 */
public final class InvalidGroupStateException extends Exception {

  InvalidGroupStateException(InvalidInputException e) {
    super(e);
  }

  InvalidGroupStateException(String message) {
    super(message);
  }

  InvalidGroupStateException() {
  }
}
