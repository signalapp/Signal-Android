package org.thoughtcrime.securesms.transport;

public class RetryLaterException extends Exception {

  private final long backoff;

  public RetryLaterException() {
    this(null, -1);
  }

  public RetryLaterException(long backoff) {
    this(null, backoff);
  }

  public RetryLaterException(Exception e) {
    this(e, -1);
  }

  public RetryLaterException(Exception e, long backoff) {
    super(e);
    this.backoff = backoff;
  }

  /**
   * @return The amount of time to wait before retrying again, or -1 if none is specified.
   */
  public long getBackoff() {
    return backoff;
  }
}
