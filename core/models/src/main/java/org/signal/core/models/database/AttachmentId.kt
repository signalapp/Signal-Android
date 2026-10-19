/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.database

import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.Parcelize

@Parcelize
data class AttachmentId(
  @JsonProperty("rowId")
  @JvmField
  val id: Long
) : Parcelable, DatabaseId {

  val isValid: Boolean
    get() = id >= 0

  override fun toString(): String {
    return "AttachmentId::$id"
  }

  override fun serialize(): String {
    return id.toString()
  }
}
