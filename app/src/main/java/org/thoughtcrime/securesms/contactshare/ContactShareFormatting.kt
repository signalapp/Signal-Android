/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalE164Util

/** Detail ids are positional, so a selection can be resolved back against the contact it came from. */
internal const val PHONE_PREFIX = "phone"
internal const val EMAIL_PREFIX = "email"
internal const val ADDRESS_PREFIX = "address"

/** Not positional, since a contact carries at most one company. */
internal const val ORGANIZATION_ID = "organization"

/** Not positional either, and both are the sharer's own private annotations. */
internal const val NICKNAME_ID = "nickname"
internal const val NOTE_ID = "note"

/** The joined form, for the single row that stands in for both parts. */
internal fun Contact.SignalNickname.displayText(): String {
  return listOfNotNull(this.given.nullIfBlank(), this.family.nullIfBlank()).joinToString(" ")
}

internal const val PHOTO_ID_ADDRESS_BOOK = "address-book"
internal const val PHOTO_ID_SIGNAL_PROFILE = "signal-profile"

/** Sharing no photo at all, which leaves the receiver to draw its own fallback from the name. */
internal const val PHOTO_ID_NONE = "none"

/** The card's claim about who it describes, absent for a card that predates ACI sharing. */
internal val Contact.signalAci: ACI?
  get() {
    if (!RemoteConfig.contactSharingV2) {
      return null
    }

    return this.aci?.let { ACI.parseOrNull(it) }?.takeIf { it.isValid }
  }

/**
 * Whether the subject of the card is reachable on Signal. An ACI is proof on its own, so this is
 * answerable for someone we have no phone number and no recipient row for, which is the whole point
 * of carrying it.
 */
internal val Contact.isOnSignal: Boolean
  get() {
    val aci = this.signalAci ?: return this.resolveSignalRecipient() != null
    val existing = SignalDatabase.recipients.getByAci(aci).orElse(null) ?: return true

    return Recipient.resolved(existing).isRegistered
  }

/** A lookup rather than an insert, so browsing contacts does not create recipient rows. */
internal fun Contact.resolveSignalRecipient(): RecipientId? {
  this.signalAci
    ?.let { SignalDatabase.recipients.getByAci(it).orElse(null) }
    ?.takeIf { Recipient.resolved(it).isRegistered }
    ?.let { return it }

  return this.phoneNumbers
    .asSequence()
    .mapNotNull { phone -> SignalE164Util.formatAsE164(phone.number) }
    .mapNotNull { e164 -> SignalDatabase.recipients.getByE164(e164).orElse(null) }
    .firstOrNull { Recipient.resolved(it).isRegistered }
}

/**
 * Resolves the subject to a recipient row, creating one from the card's ACI when there is none.
 *
 * Only for paths the user explicitly asked for, since a row is a durable side effect. Passively
 * receiving a card must never seed one, or a sender could plant arbitrary rows by sending cards.
 */
@WorkerThread
internal fun Contact.resolveOrCreateSignalRecipient(): RecipientId? {
  this.resolveSignalRecipient()?.let { return it }

  val aci = this.signalAci ?: return null
  val id = SignalDatabase.recipients.getOrInsertFromServiceId(aci)

  SignalDatabase.recipients.setSharedName(id, ProfileName.fromParts(this.name.givenName, this.name.familyName))

  return id
}

internal fun Contact.Phone.labelText(context: Context): String {
  return when (this.type) {
    Contact.Phone.Type.HOME -> context.getString(R.string.ContactShareEditActivity_type_home)
    Contact.Phone.Type.MOBILE -> context.getString(R.string.ContactShareEditActivity_type_mobile)
    Contact.Phone.Type.WORK -> context.getString(R.string.ContactShareEditActivity_type_work)
    Contact.Phone.Type.CUSTOM -> this.label.orEmpty()
    else -> ""
  }
}

internal fun Contact.Email.labelText(context: Context): String {
  return when (this.type) {
    Contact.Email.Type.HOME -> context.getString(R.string.ContactShareEditActivity_type_home)
    Contact.Email.Type.MOBILE -> context.getString(R.string.ContactShareEditActivity_type_mobile)
    Contact.Email.Type.WORK -> context.getString(R.string.ContactShareEditActivity_type_work)
    Contact.Email.Type.CUSTOM -> this.label.orEmpty()
    else -> ""
  }
}

internal fun Contact.PostalAddress.labelText(context: Context): String {
  return when (this.type) {
    Contact.PostalAddress.Type.HOME -> context.getString(R.string.ContactShareEditActivity_type_home)
    Contact.PostalAddress.Type.WORK -> context.getString(R.string.ContactShareEditActivity_type_work)
    Contact.PostalAddress.Type.CUSTOM -> this.label ?: context.getString(R.string.ContactShareEditActivity_type_missing)
    else -> context.getString(R.string.ContactShareEditActivity_type_missing)
  }
}

internal fun Contact.PostalAddress.displayLines(): List<String> = this.toString().lines().filter { it.isNotBlank() }
