/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import android.os.Parcel
import kotlinx.parcelize.Parceler
import kotlinx.serialization.json.Json
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.network.api.RegistrationApiV2.SvrCredentials

/**
 * The registration network models live in a pure-JVM module and therefore cannot implement
 * [android.os.Parcelable] themselves. They are all `@Serializable`, so we parcel them as JSON.
 */
object SessionMetadataParceler : Parceler<SessionMetadata> {
  override fun create(parcel: Parcel): SessionMetadata {
    return Json.decodeFromString(parcel.readString()!!)
  }

  override fun SessionMetadata.write(parcel: Parcel, flags: Int) {
    parcel.writeString(Json.encodeToString(this))
  }
}

object NullableSessionMetadataParceler : Parceler<SessionMetadata?> {
  override fun create(parcel: Parcel): SessionMetadata? {
    return parcel.readString()?.let { Json.decodeFromString(it) }
  }

  override fun SessionMetadata?.write(parcel: Parcel, flags: Int) {
    parcel.writeString(this?.let { Json.encodeToString(it) })
  }
}

object SvrCredentialsParceler : Parceler<SvrCredentials> {
  override fun create(parcel: Parcel): SvrCredentials {
    return Json.decodeFromString(parcel.readString()!!)
  }

  override fun SvrCredentials.write(parcel: Parcel, flags: Int) {
    parcel.writeString(Json.encodeToString(this))
  }
}
