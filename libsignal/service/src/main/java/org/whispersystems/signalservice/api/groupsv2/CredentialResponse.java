package org.whispersystems.signalservice.api.groupsv2;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CredentialResponse {

  @JsonProperty
  private TemporalCredential[] credentials;

  @JsonProperty
  private TemporalCredential[] callLinkAuthCredentials;

  public TemporalCredential[] getCredentials() {
    return credentials;
  }

  public TemporalCredential[] getCallLinkAuthCredentials() {
    return callLinkAuthCredentials;
  }
}
