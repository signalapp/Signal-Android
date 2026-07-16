/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.createprofile

sealed class CreateProfileScreenEvents {
  data class GivenNameChanged(val value: String) : CreateProfileScreenEvents() {
    override fun toString(): String = "GivenNameChanged(value=${value.length} chars)"
  }
  data class FamilyNameChanged(val value: String) : CreateProfileScreenEvents() {
    override fun toString(): String = "FamilyNameChanged(value=${value.length} chars)"
  }
  data class AvatarSelected(val bytes: ByteArray) : CreateProfileScreenEvents() {
    override fun toString(): String = "AvatarSelected(${bytes.size} bytes)"
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is AvatarSelected) return false
      return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
  }
  data object AvatarCleared : CreateProfileScreenEvents()
  data object WhoCanFindMeClicked : CreateProfileScreenEvents()
  data class DiscoverabilityChanged(val discoverable: Boolean) : CreateProfileScreenEvents()
  data object NextClicked : CreateProfileScreenEvents()
  data object UploadFailedDialogDismissed : CreateProfileScreenEvents()
}
