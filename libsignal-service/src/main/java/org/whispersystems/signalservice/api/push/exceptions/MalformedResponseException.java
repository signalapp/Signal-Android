package org.whispersystems.signalservice.api.push.exceptions;

import java.io.IOException;

/**
 * Indicates that a response is malformed or otherwise in an unexpected format.
 */
public class MalformedResponseException extends IOException {

  public MalformedResponseException(String message) {
    super(message);
  }

  public MalformedResponseException(String message, IOException e) {
    super(message, e);
  }
}
