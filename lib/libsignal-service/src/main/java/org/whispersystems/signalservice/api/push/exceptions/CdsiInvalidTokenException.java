package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;


/**
 * Indicates that you provided a bad token to CDSI.
 */
public class CdsiInvalidTokenException extends NonSuccessfulResponseCodeException {
  public CdsiInvalidTokenException() {
    super(4101);
  }
}
