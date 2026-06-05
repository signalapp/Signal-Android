package org.whispersystems.signalservice.internal.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

/**
 * Indicates that the unidentified authorization header provided to the multi_recipient endpoint
 * was incorrect (i.e. one or more of your unauthorized access keys is invalid);
 */
public class InvalidUnidentifiedAccessHeaderException extends NonSuccessfulResponseCodeException {

  public InvalidUnidentifiedAccessHeaderException() {
    super(401);
  }
}
