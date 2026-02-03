/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.media

import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Properties that describe transformations to be applied to media before sending.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class TransformProperties(
  @EncodeDefault(EncodeDefault.Mode.ALWAYS)
  @JsonProperty("skipTransform")
  @SerialName("skipTransform")
  @JvmField
  val skipTransform: Boolean = false,

  @EncodeDefault(EncodeDefault.Mode.ALWAYS)
  @JsonProperty("videoTrim")
  @SerialName("videoTrim")
  @JvmField
  val videoTrim: Boolean = false,

  @EncodeDefault(EncodeDefault.Mode.ALWAYS)
  @JsonProperty("videoTrimStartTimeUs")
  @SerialName("videoTrimStartTimeUs")
  @JvmField
  val videoTrimStartTimeUs: Long = 0,

  @EncodeDefault(EncodeDefault.Mode.ALWAYS)
  @JsonProperty("videoTrimEndTimeUs")
  @SerialName("videoTrimEndTimeUs")
  @JvmField
  val videoTrimEndTimeUs: Long = 0,

  @EncodeDefault(EncodeDefault.Mode.ALWAYS)
  @JsonProperty("sentMediaQuality")
  @SerialName("sentMediaQuality")
  @JvmField
  val sentMediaQuality: Int = DEFAULT_MEDIA_QUALITY,

  @EncodeDefault(EncodeDefault.Mode.ALWAYS)
  @JsonProperty("mp4Faststart")
  @SerialName("mp4Faststart")
  @JvmField
  val mp4FastStart: Boolean = false
) : Parcelable {
  fun shouldSkipTransform(): Boolean {
    return skipTransform
  }

  @IgnoredOnParcel
  @EncodeDefault(EncodeDefault.Mode.ALWAYS)
  @JsonProperty("videoEdited")
  @SerialName("videoEdited")
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
