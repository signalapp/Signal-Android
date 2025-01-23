package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is a parsed, POJO representation of the server response describing the state of the registration session.
 */
data class RegistrationSessionMetadataResponse(
  val metadata: RegistrationSessionMetadataJson,
  val clientReceivedAt: Duration,
  val retryAfterTimestamp: Duration? = null
) {
  constructor(metadata: RegistrationSessionMetadataJson, clientReceivedAt: Long, retryAfterTimestamp: Long?) : this(metadata, clientReceivedAt.milliseconds, retryAfterTimestamp?.milliseconds)

  fun deriveTimestamp(delta: Duration?): Duration {
    if (delta == null) {
      return 0.milliseconds
    }

    val now = System.currentTimeMillis().milliseconds
    val base = clientReceivedAt.takeIf { clientReceivedAt <= now } ?: now

    return base + delta
  }
}

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
