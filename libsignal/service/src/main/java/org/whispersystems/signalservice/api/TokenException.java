package org.whispersystems.signalservice.api;

import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

class TokenException extends Exception {

  private final TokenResponse nextToken;
  private final boolean       canAutomaticallyRetry;

  TokenException(TokenResponse nextToken, boolean canAutomaticallyRetry) {
    this.nextToken             = nextToken;
    this.canAutomaticallyRetry = canAutomaticallyRetry;
  }

  public TokenResponse getToken() {
    return nextToken;
  }

  public boolean isCanAutomaticallyRetry() {
    return canAutomaticallyRetry;
  }
}
