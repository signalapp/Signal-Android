package org.whispersystems.signalservice.api.push.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.internal.util.JsonUtil;

import io.reactivex.rxjava3.annotations.NonNull;

/**
 * Response indicating we gave the server a non-normalized phone number. The expected normalized version of the number is provided.
 */
public class NonNormalizedPhoneNumberException extends NonSuccessfulResponseCodeException {

  private final String originalNumber;
  private final String normalizedNumber;

  public static NonNormalizedPhoneNumberException forResponse(@NonNull String responseBody) throws MalformedResponseException {
    JsonResponse response = JsonUtil.fromJsonResponse(responseBody, JsonResponse.class);
    return new NonNormalizedPhoneNumberException(response.originalNumber, response.normalizedNumber);
  }

  public NonNormalizedPhoneNumberException(String originalNumber, String normalizedNumber) {
    super(400);

    this.originalNumber   = originalNumber;
    this.normalizedNumber = normalizedNumber;
  }

  public String getOriginalNumber() {
    return originalNumber;
  }

  public String getNormalizedNumber() {
    return normalizedNumber;
  }

  private static class JsonResponse {
    @JsonProperty
    private String originalNumber;

    @JsonProperty
    private String normalizedNumber;
  }
}
