package org.whispersystems.signalservice.api.push.exceptions;

/**
 * An exception indicating that the server believes the number provided is 'impossible', meaning it fails the most basic libphonenumber checks.
 */
public class ImpossiblePhoneNumberException extends NonSuccessfulResponseCodeException {
  public ImpossiblePhoneNumberException() {
    super(400);
  }
}
