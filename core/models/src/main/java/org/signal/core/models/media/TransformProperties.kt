/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.media

import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Properties that describe transformations to be applied to media before sending.
 */
@Serializable
@Parcelize
data class TransformProperties(
  @JsonProperty("skipTransform")
  @JvmField
  val skipTransform: Boolean = false,

  @JsonProperty("videoTrim")
  @JvmField
  val videoTrim: Boolean = false,

  @JsonProperty("videoTrimStartTimeUs")
  @JvmField
  val videoTrimStartTimeUs: Long = 0,

  @JsonProperty("videoTrimEndTimeUs")
  @JvmField
  val videoTrimEndTimeUs: Long = 0,

  @JsonProperty("sentMediaQuality")
  @JvmField
  val sentMediaQuality: Int = DEFAULT_MEDIA_QUALITY,

  @JsonProperty("mp4Faststart")
  @JvmField
  val mp4FastStart: Boolean = false
) : Parcelable {
  fun shouldSkipTransform(): Boolean {
    return skipTransform
  }

  @IgnoredOnParcel
  @JsonProperty("videoEdited")
  val videoEdited: Boolean = videoTrim

  fun withSkipTransform(): TransformProperties {
    return this.copy(
      skipTransform = true
    )
  }

  fun withMp4FastStart(): TransformProperties {
    return this.copy(mp4FastStart = true)
  }

  companion object {
    /** Corresponds to SentMediaQuality.STANDARD.code */
    const val DEFAULT_MEDIA_QUALITY = 0

    @JvmStatic
    fun empty(): TransformProperties {
      return TransformProperties(
        skipTransform = false,
        videoTrim = false,
        videoTrimStartTimeUs = 0,
        videoTrimEndTimeUs = 0,
        sentMediaQuality = DEFAULT_MEDIA_QUALITY,
        mp4FastStart = false
      )
    }

    fun forSkipTransform(): TransformProperties {
      return TransformProperties(
        skipTransform = true,
        videoTrim = false,
        videoTrimStartTimeUs = 0,
        videoTrimEndTimeUs = 0,
        sentMediaQuality = DEFAULT_MEDIA_QUALITY,
        mp4FastStart = false
      )
    }

    fun forVideoTrim(videoTrimStartTimeUs: Long, videoTrimEndTimeUs: Long): TransformProperties {
      return TransformProperties(
        skipTransform = false,
        videoTrim = true,
        videoTrimStartTimeUs = videoTrimStartTimeUs,
        videoTrimEndTimeUs = videoTrimEndTimeUs,
        sentMediaQuality = DEFAULT_MEDIA_QUALITY,
        mp4FastStart = false
      )
    }

    @JvmStatic
    fun forSentMediaQuality(sentMediaQuality: Int): TransformProperties {
      return TransformProperties(sentMediaQuality = sentMediaQuality)
    }
  }
}
