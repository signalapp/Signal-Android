package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;


/**
 * Indicates that something about our request was wrong. Could be:
 * - Over 50k new contacts
 * - Missing version byte prefix
 * - Missing credentials
 * - E164s are not a multiple of 8 bytes
 * - Something else?
 */
public class CdsiInvalidArgumentException extends NonSuccessfulResponseCodeException {
  public CdsiInvalidArgumentException() {
    super(4003);
  }
}
