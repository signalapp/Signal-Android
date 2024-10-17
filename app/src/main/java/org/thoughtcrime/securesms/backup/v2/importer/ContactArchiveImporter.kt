/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.thoughtcrime.securesms.backup.v2.proto.Contact
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI

/**
 * Handles the importing of [Contact] models into the local database.
 */
object ContactArchiveImporter {
  fun import(contact: Contact): RecipientId {
    val id = SignalDatabase.recipients.getAndPossiblyMergePnpVerified(
      aci = ACI.parseOrNull(contact.aci?.toByteArray()),
      pni = PNI.parseOrNull(contact.pni?.toByteArray()),
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
      RecipientTable.EXTRAS to contact.toLocalExtras().encode()
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

private fun Contact.toLocalExtras(): RecipientExtras {
  return RecipientExtras(
    hideStory = this.hideStory
  )
}

private val Contact.formattedE164: String?
  get() {
    return e164?.let {
      PhoneNumberFormatter.get(AppDependencies.application).format(e164.toString())
    }
  }
