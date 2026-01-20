/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import org.signal.core.models.media.TransformProperties
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException
import java.util.Optional

private val TAG = Log.tag(TransformProperties::class.java)

/**
 * Serializes the TransformProperties to a JSON string using Jackson.
 */
fun TransformProperties.serialize(): String {
  return JsonUtil.toJson(this)
}

/**
 * Parses a JSON string to create a TransformProperties instance.
 */
fun parseTransformProperties(serialized: String?): TransformProperties {
  return if (serialized == null) {
    TransformProperties.empty()
  } else {
    try {
      JsonUtil.fromJson(serialized, TransformProperties::class.java)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse TransformProperties!", e)
      TransformProperties.empty()
    }
  }
}

/**
 * Creates TransformProperties for the given media quality, preserving existing properties.
 */
fun transformPropertiesForSentMediaQuality(currentProperties: Optional<TransformProperties>, sentMediaQuality: SentMediaQuality): TransformProperties {
  val existing = currentProperties.orElse(TransformProperties.empty())
  return existing.copy(sentMediaQuality = sentMediaQuality.code)
}
