package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents the response when fetching the archive backup key rotation limits
 */
data class ArchiveKeyRotationLimitResponse(
  @JsonProperty val hasPermitsRemaining: Boolean?,
  @JsonProperty val retryAfterSeconds: Long?
)
