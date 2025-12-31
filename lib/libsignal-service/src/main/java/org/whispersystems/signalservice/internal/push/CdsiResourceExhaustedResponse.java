package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for {@link org.whispersystems.signalservice.api.push.exceptions.CdsiResourceExhaustedException}
 */
public class CdsiResourceExhaustedResponse {
  @JsonProperty("retry_after")
  private int retryAfter;

  public int getRetryAfter() {
    return retryAfter;
  }
}
