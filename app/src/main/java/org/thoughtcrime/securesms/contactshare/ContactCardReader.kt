/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import ezvcard.Ezvcard
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto
import org.thoughtcrime.securesms.contactshare.Contact.Avatar
import org.thoughtcrime.securesms.contactshare.Contact.Email
import org.thoughtcrime.securesms.contactshare.Contact.Name
import org.thoughtcrime.securesms.contactshare.Contact.Phone
import org.thoughtcrime.securesms.contactshare.Contact.PostalAddress
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.SignalE164Util

/** Reads a contact card off a uri, either an address book entry or a vcard attachment. */
class ContactCardReader(context: Context) {

  companion object {
    private val TAG = Log.tag(ContactCardReader::class)
  }

  private val context: Context = context.applicationContext

  /** An address book entry, identified by a [ContactsContract] uri. Needs `READ_CONTACTS`, since the details live in the data table. */
  @WorkerThread
  fun readSystemContact(uri: Uri): Contact? {
    return try {
      fromSystemContacts(ContactUtil.getContactIdFromUri(uri))
    } catch (e: SecurityException) {
      Log.w(TAG, "Not allowed to read the selected contact.", e)
      null
    }
  }

  /**
   * The single phone number a system picker handed back, as a [SharedContactSource.SystemPhone] card.
   *
   * The provider reports the name as one string rather than as parts, so it goes in the given name and the
   * editor is where it gets split.
   */
  @WorkerThread
  fun readSystemPhone(uri: Uri): Contact? {
    val picked = try {
      SystemContactsRepository.getPickedPhone(context, uri)
    } catch (e: SecurityException) {
      Log.w(TAG, "Not allowed to read the picked phone number.", e)
      null
    } ?: return null

    val number = ContactUtil.getNormalizedPhoneNumber(picked.number)

    if (number == null) {
      Log.w(TAG, "The picked phone number could not be normalized.")
      return null
    }

    val name = Name(picked.displayName?.takeUnless { it.isBlank() }, null, null, null, null, null)

    if (name.isEmpty) {
      Log.w(TAG, "The picked phone number has no name to render.")
      return null
    }

    val phones = listOf(Phone(number, VCardUtil.phoneTypeFromContactType(picked.type), picked.label))

    return Contact(name, null, phones, emptyList(), emptyList(), signalAvatar(phones))
  }

  /** A .vcf, which carries no contact id and so cannot go through [readSystemContact]. */
  @WorkerThread
  fun readVCard(uri: Uri): Contact? {
    return fromVcard(uri)
  }

  private fun fromSystemContacts(contactId: Long): Contact? {
    val phoneNumbers = phoneNumbers(contactId)
    val emails = emails(contactId)
    val postalAddresses = postalAddresses(contactId)
    val name = name(contactId)
    val organization = SystemContactsRepository.getOrganizationName(context, contactId)

    if (name == null && organization.isNullOrEmpty()) {
      Log.w(TAG, "The selected contact has no name to render.")
      return null
    }

    if (phoneNumbers.isEmpty() && emails.isEmpty() && postalAddresses.isEmpty()) {
      Log.w(TAG, "The selected contact has no details to share.")
      return null
    }

    val avatar = avatar(contactId, phoneNumbers)

    return Contact(name, organization, phoneNumbers, emails, postalAddresses, avatar)
  }

  private fun fromVcard(uri: Uri): Contact? {
    val contact = try {
      PartAuthority.getAttachmentStream(context, uri).use { stream ->
        val vcard = Ezvcard.parse(stream).first() ?: return@use null
        VCardUtil.getContactFromVcard(vcard)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse the vcard.", e)
      null
    }

    if (AppDependencies.blobs.authority == uri.authority) {
      AppDependencies.blobs.delete(context, uri)
    }

    return contact
  }

  private fun name(contactId: Long): Name? {
    val details = SystemContactsRepository.getNameDetails(context, contactId) ?: return null
    val name = Name(details.givenName, details.familyName, details.prefix, details.suffix, details.middleName, null)

    return name.takeUnless { it.isEmpty }
  }

  /** First entry per number wins, preferring one with a type or label. */
  private fun phoneNumbers(contactId: Long): List<Phone> {
    val byNumber = linkedMapOf<String, Phone>()

    for (details in SystemContactsRepository.getPhoneDetails(context, contactId)) {
      val number = ContactUtil.getNormalizedPhoneNumber(details.number) ?: continue
      val existing = byNumber[number]

      if (existing == null || (existing.type == Phone.Type.CUSTOM && existing.label == null)) {
        byNumber[number] = Phone(number, VCardUtil.phoneTypeFromContactType(details.type), details.label)
      }
    }

    return byNumber.values.toList()
  }

  private fun emails(contactId: Long): List<Email> {
    return SystemContactsRepository.getEmailDetails(context, contactId)
      .mapNotNull { details ->
        details.address?.let { Email(it, VCardUtil.emailTypeFromContactType(details.type), details.label) }
      }
  }

  private fun postalAddresses(contactId: Long): List<PostalAddress> {
    return SystemContactsRepository.getPostalAddressDetails(context, contactId)
      .map { details ->
        PostalAddress(
          VCardUtil.postalAddressTypeFromContactType(details.type),
          details.label,
          details.street,
          details.poBox,
          details.neighborhood,
          details.city,
          details.region,
          details.postal,
          details.country
        )
      }
  }

  /** The address book photo, else the Signal photo of whoever the numbers match. */
  private fun avatar(contactId: Long, phoneNumbers: List<Phone>): Avatar? {
    SystemContactsRepository.getAvatarUri(context, contactId)?.let { uri ->
      return Avatar(uri, false)
    }

    return signalAvatar(phoneNumbers)
  }

  private fun signalAvatar(phoneNumbers: List<Phone>): Avatar? {
    return phoneNumbers
      .asSequence()
      .mapNotNull { SignalE164Util.formatAsE164(it.number) }
      .mapNotNull { recipientAvatar(it) }
      .firstOrNull()
  }

  private fun recipientAvatar(e164: String): Avatar? {
    val photo: ContactPhoto = Recipient.external(e164)?.contactPhoto ?: return null
    val uri = photo.getUri(context) ?: return null

    return Avatar(uri, photo.isProfilePhoto)
  }
}
