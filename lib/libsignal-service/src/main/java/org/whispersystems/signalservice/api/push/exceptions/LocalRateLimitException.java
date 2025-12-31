package org.whispersystems.signalservice.api.push.exceptions;

/**
 * Thrown when self limiting networking.
 */
public final class LocalRateLimitException extends Exception {
  public LocalRateLimitException() { }
}
