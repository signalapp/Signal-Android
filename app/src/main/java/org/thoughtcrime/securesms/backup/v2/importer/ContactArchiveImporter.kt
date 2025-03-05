/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.insertInto
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.thoughtcrime.securesms.backup.v2.proto.Contact
import org.thoughtcrime.securesms.backup.v2.util.toLocal
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.SignalE164Util
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI

/**
 * Handles the importing of [Contact] models into the local database.
 */
object ContactArchiveImporter {
  fun import(contact: Contact): RecipientId {
    val aci = ACI.parseOrNull(contact.aci?.toByteArray())
    val pni = PNI.parseOrNull(contact.pni?.toByteArray())

    val id = SignalDatabase.recipients.getAndPossiblyMergePnpVerified(
      aci = aci,
      pni = pni,
      e164 = contact.formattedE164
    )

    val profileKey = contact.profileKey?.toByteArray()
    val values = contentValuesOf(
      RecipientTable.BLOCKED to contact.blocked,
      RecipientTable.HIDDEN to contact.visibility.toLocal().serialize(),
      RecipientTable.TYPE to RecipientTable.RecipientType.INDIVIDUAL.id,
      RecipientTable.PROFILE_FAMILY_NAME to contact.profileFamilyName,
      RecipientTable.PROFILE_GIVEN_NAME to contact.profileGivenName,
      RecipientTable.PROFILE_JOINED_NAME to ProfileName.fromParts(contact.profileGivenName, contact.profileFamilyName).toString(),
      RecipientTable.PROFILE_KEY to if (profileKey == null) null else Base64.encodeWithPadding(profileKey),
      RecipientTable.PROFILE_SHARING to contact.profileSharing.toInt(),
      RecipientTable.USERNAME to contact.username,
      RecipientTable.EXTRAS to contact.toLocalExtras().encode(),
      RecipientTable.NOTE to contact.note,
      RecipientTable.NICKNAME_GIVEN_NAME to contact.nickname?.given,
      RecipientTable.NICKNAME_FAMILY_NAME to contact.nickname?.family,
      RecipientTable.SYSTEM_GIVEN_NAME to contact.systemGivenName,
      RecipientTable.SYSTEM_FAMILY_NAME to contact.systemFamilyName,
      RecipientTable.SYSTEM_NICKNAME to contact.systemNickname,
      RecipientTable.AVATAR_COLOR to contact.avatarColor?.toLocal()?.serialize()
    )

    if (contact.registered != null) {
      values.put(RecipientTable.UNREGISTERED_TIMESTAMP, 0L)
      values.put(RecipientTable.REGISTERED, RecipientTable.RegisteredState.REGISTERED.id)
    } else if (contact.notRegistered != null) {
      values.put(RecipientTable.UNREGISTERED_TIMESTAMP, contact.notRegistered.unregisteredTimestamp)
      values.put(RecipientTable.REGISTERED, RecipientTable.RegisteredState.NOT_REGISTERED.id)
    }

    SignalDatabase.writableDatabase
      .update(RecipientTable.TABLE_NAME)
      .values(values)
      .where("${RecipientTable.ID} = ?", id)
      .run()

    if (contact.identityKey != null && (aci != null || pni != null)) {
      SignalDatabase.writableDatabase
        .insertInto(IdentityTable.TABLE_NAME)
        .values(
          IdentityTable.ADDRESS to (aci ?: pni).toString(),
          IdentityTable.IDENTITY_KEY to Base64.encodeWithPadding(contact.identityKey.toByteArray()),
          IdentityTable.VERIFIED to contact.identityState.toLocal().toInt()
        )
        .run(SQLiteDatabase.CONFLICT_REPLACE)
    }

    return id
  }
}

private fun Contact.Visibility.toLocal(): Recipient.HiddenState {
  return when (this) {
    Contact.Visibility.VISIBLE -> Recipient.HiddenState.NOT_HIDDEN
    Contact.Visibility.HIDDEN -> Recipient.HiddenState.HIDDEN
    Contact.Visibility.HIDDEN_MESSAGE_REQUEST -> Recipient.HiddenState.HIDDEN_MESSAGE_REQUEST
  }
}

private fun Contact.IdentityState.toLocal(): IdentityTable.VerifiedStatus {
  return when (this) {
    Contact.IdentityState.DEFAULT -> IdentityTable.VerifiedStatus.DEFAULT
    Contact.IdentityState.VERIFIED -> IdentityTable.VerifiedStatus.VERIFIED
    Contact.IdentityState.UNVERIFIED -> IdentityTable.VerifiedStatus.UNVERIFIED
  }
}

private fun Contact.toLocalExtras(): RecipientExtras {
  return RecipientExtras(
    hideStory = this.hideStory
  )
}

private val Contact.formattedE164: String?
  get() {
    return e164?.let {
      SignalE164Util.formatAsE164("+$e164")
    }
  }
