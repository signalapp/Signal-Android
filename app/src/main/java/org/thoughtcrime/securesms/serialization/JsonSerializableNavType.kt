/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.serialization

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * NavType for JSON Serializable types using KotlinX Serialization.
 *
 * This only needs to be used if there are embedded serializers (using `@Serializable(with = ...)`) as the
 * native NavType serialization support doesn't handle these objects gracefully.
 */
class JsonSerializableNavType<T : Any>(
  private val serializer: KSerializer<T>
) : NavType<T>(false) {
  override fun get(bundle: Bundle, key: String): T? {
    return Json.Default.decodeFromString(serializer, bundle.getString(key)!!)
  }

  override fun parseValue(value: String): T {
    return Json.Default.decodeFromString(serializer, value)
  }

  override fun put(bundle: Bundle, key: String, value: T) {
    bundle.putString(key, Json.Default.encodeToString(serializer, value))
  }

  override fun serializeAsValue(value: T): String {
    return Uri.encode(Json.Default.encodeToString(serializer, value))
  }
}
