package org.whispersystems.signalservice.api.push.exceptions;

/**
 * A 4008 responses from CDSI indicating we've exhausted our quota.
 */
public class CdsiResourceExhaustedException extends NonSuccessfulResponseCodeException {

  private final int retryAfterSeconds;

  public CdsiResourceExhaustedException(int retryAfterSeconds) {
    super(4008);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
