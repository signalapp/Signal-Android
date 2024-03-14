package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class SubmitRecaptchaChallengePayload {

  @JsonProperty
  private String type;

  @JsonProperty
  private String token;

  @JsonProperty
  private String captcha;

  public SubmitRecaptchaChallengePayload() {}

  public SubmitRecaptchaChallengePayload(String challenge, String recaptchaToken) {
    this.type    = "captcha";
    this.token   = challenge;
    this.captcha = recaptchaToken;
  }
}
