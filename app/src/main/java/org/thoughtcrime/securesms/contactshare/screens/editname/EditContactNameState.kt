/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.editname

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** The structured name of a shared contact, matching the address book fields. */
@Parcelize
data class ContactNameParts(
  val prefix: String = "",
  val givenName: String = "",
  val middleName: String = "",
  val familyName: String = "",
  val suffix: String = "",
  val organization: String = ""
) : Parcelable {
  /** A prefix or suffix alone is not a name. */
  val hasDisplayableName: Boolean
    get() = givenName.isNotBlank() || middleName.isNotBlank() || familyName.isNotBlank() || organization.isNotBlank()

  /** Whether anything outside the company is left, including the parts that cannot stand alone. */
  val hasPersonalName: Boolean
    get() = prefix.isNotBlank() || givenName.isNotBlank() || middleName.isNotBlank() || familyName.isNotBlank() || suffix.isNotBlank()

  override fun toString(): String = "ContactNameParts(hasPrefix=${prefix.isNotBlank()}, hasGiven=${givenName.isNotBlank()}, hasMiddle=${middleName.isNotBlank()}, hasFamily=${familyName.isNotBlank()}, hasSuffix=${suffix.isNotBlank()}, hasOrg=${organization.isNotBlank()})"
}

/** State for the "Edit name" screen. */
data class EditContactNameState(
  val parts: ContactNameParts = ContactNameParts(),
  /** The name as the editor opened, to tell whether anything changed. */
  val original: ContactNameParts = ContactNameParts()
) {
  val canSave: Boolean
    get() = parts != original && parts.hasDisplayableName
}
