package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This is a parsed, POJO representation of the server response describing the state of the registration session.
 * The useful headers and the request body are wrapped in a single holder class.
 */
data class RegistrationSessionMetadataResponse(
  val headers: RegistrationSessionMetadataHeaders,
  val body: RegistrationSessionMetadataJson,
  val state: RegistrationSessionState?
)

data class RegistrationSessionMetadataHeaders(
  val timestamp: Long
)

data class RegistrationSessionMetadataJson(
  @JsonProperty("id") val id: String,
  @JsonProperty("nextSms") val nextSms: Int?,
  @JsonProperty("nextCall") val nextCall: Int?,
  @JsonProperty("nextVerificationAttempt") val nextVerificationAttempt: Int?,
  @JsonProperty("allowedToRequestCode") val allowedToRequestCode: Boolean,
  @JsonProperty("requestedInformation") val requestedInformation: List<String>,
  @JsonProperty("verified") val verified: Boolean
) {
  fun pushChallengedRequired(): Boolean {
    return requestedInformation.contains("pushChallenge")
  }

  fun captchaRequired(): Boolean {
    return requestedInformation.contains("captcha")
  }
}

data class RegistrationSessionState(
  var pushChallengeTimedOut: Boolean
)
