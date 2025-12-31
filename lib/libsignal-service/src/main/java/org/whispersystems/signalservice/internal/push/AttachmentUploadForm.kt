package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents an attachment upload form that can be returned by various service endpoints.
 */
data class AttachmentUploadForm(
  @JvmField
  @JsonProperty
  val cdn: Int,

  @JvmField
  @JsonProperty
  val key: String,

  @JvmField
  @JsonProperty
  val headers: Map<String, String>,

  @JvmField
  @JsonProperty
  val signedUploadLocation: String
)
