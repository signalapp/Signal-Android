package org.whispersystems.signalservice.internal.contacts.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class MultiRemoteAttestationResponse {

  @JsonProperty
  private Map<String, RemoteAttestationResponse> attestations;

  public Map<String, RemoteAttestationResponse> getAttestations() {
    return attestations;
  }
}
