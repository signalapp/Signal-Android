package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class SubmitPushChallengePayload {

  @JsonProperty
  private String type;

  @JsonProperty
  private String challenge;

  public SubmitPushChallengePayload() {}

  public SubmitPushChallengePayload(String challenge) {
    this.type      = "rateLimitPushChallenge";
    this.challenge = challenge;
  }
}
