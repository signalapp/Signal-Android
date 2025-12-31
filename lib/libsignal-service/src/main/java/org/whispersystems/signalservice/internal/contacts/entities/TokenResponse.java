package org.whispersystems.signalservice.internal.contacts.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenResponse {

  @JsonProperty
  private byte[] backupId;

  @JsonProperty
  private byte[] token;

  @JsonProperty
  private int tries;

  @JsonCreator
  public TokenResponse() {
  }

  public TokenResponse(byte[] backupId, byte[] token, int tries) {
    this.backupId = backupId;
    this.token    = token;
    this.tries    = tries;
  }

  public byte[] getBackupId() {
    return backupId;
  }

  public byte[] getToken() {
    return token;
  }

  public int getTries() {
    return tries;
  }
}
