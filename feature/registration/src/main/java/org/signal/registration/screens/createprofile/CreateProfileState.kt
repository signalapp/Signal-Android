/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.createprofile

import org.signal.registration.util.DebugLoggable
import org.signal.registration.util.DebugLoggableModel

data class CreateProfileState(
  val givenName: String = "",
  val familyName: String = "",
  val avatar: ByteArray? = null,
  val discoverableByPhoneNumber: Boolean = true,
  val isLoading: Boolean = true,
  val isSubmitting: Boolean = false,
  val oneTimeEvent: OneTimeEvent? = null
) : DebugLoggableModel() {

  val isFormValid: Boolean
    get() = givenName.trim().isNotEmpty()

  override fun toSafeString(): String {
    return "CreateProfileState(givenName=${givenName.length} chars, familyName=${familyName.length} chars, avatar=${avatar?.size ?: 0} bytes, discoverableByPhoneNumber=$discoverableByPhoneNumber, isLoading=$isLoading, isSubmitting=$isSubmitting, oneTimeEvent=$oneTimeEvent)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CreateProfileState) return false
    if (givenName != other.givenName) return false
    if (familyName != other.familyName) return false
    if (avatar != null) {
      if (other.avatar == null) return false
      if (!avatar.contentEquals(other.avatar)) return false
    } else if (other.avatar != null) {
      return false
    }
    if (discoverableByPhoneNumber != other.discoverableByPhoneNumber) return false
    if (isLoading != other.isLoading) return false
    if (isSubmitting != other.isSubmitting) return false
    if (oneTimeEvent != other.oneTimeEvent) return false
    return true
  }

  override fun hashCode(): Int {
    var result = givenName.hashCode()
    result = 31 * result + familyName.hashCode()
    result = 31 * result + (avatar?.contentHashCode() ?: 0)
    result = 31 * result + discoverableByPhoneNumber.hashCode()
    result = 31 * result + isLoading.hashCode()
    result = 31 * result + isSubmitting.hashCode()
    result = 31 * result + (oneTimeEvent?.hashCode() ?: 0)
    return result
  }

  sealed interface OneTimeEvent : DebugLoggable {
    data object UploadFailed : OneTimeEvent {
      override fun toString(): String = "UploadFailed"
    }
  }
}
