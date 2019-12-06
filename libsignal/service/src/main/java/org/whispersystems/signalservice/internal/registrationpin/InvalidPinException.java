package org.whispersystems.signalservice.internal.registrationpin;

public final class InvalidPinException extends Exception {

  InvalidPinException(String message) {
    super(message);
  }
}
