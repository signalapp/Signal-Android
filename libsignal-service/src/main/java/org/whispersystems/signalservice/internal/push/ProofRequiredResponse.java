package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ProofRequiredResponse {

  @JsonProperty
  public String token;

  @JsonProperty
  public List<String> options;

  public ProofRequiredResponse() {}

  public String getToken() {
    return token;
  }

  public List<String> getOptions() {
    return options;
  }
}
