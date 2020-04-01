package org.whispersystems.signalservice.api.groupsv2;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CredentialResponse {

  @JsonProperty
  private TemporalCredential[] credentials;

  public TemporalCredential[] getCredentials() {
    return credentials;
  }
}
