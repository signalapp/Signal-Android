package org.whispersystems.signalservice.api;

import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

public final class KbsPinData {

  private final MasterKey     masterKey;
  private final TokenResponse tokenResponse;

  KbsPinData(MasterKey masterKey, TokenResponse tokenResponse) {
    this.masterKey     = masterKey;
    this.tokenResponse = tokenResponse;
  }

  public MasterKey getMasterKey() {
    return masterKey;
  }

  public TokenResponse getTokenResponse() {
    return tokenResponse;
  }

  public int getRemainingTries() {
    return tokenResponse.getTries();
  }
}
