/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contactshare.ADDRESS_PREFIX
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactCardReader
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.contactshare.EMAIL_PREFIX
import org.thoughtcrime.securesms.contactshare.NICKNAME_ID
import org.thoughtcrime.securesms.contactshare.NOTE_ID
import org.thoughtcrime.securesms.contactshare.ORGANIZATION_ID
import org.thoughtcrime.securesms.contactshare.PHONE_PREFIX
import org.thoughtcrime.securesms.contactshare.PHOTO_ID_ADDRESS_BOOK
import org.thoughtcrime.securesms.contactshare.PHOTO_ID_NONE
import org.thoughtcrime.securesms.contactshare.PHOTO_ID_SIGNAL_PROFILE
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.contactshare.displayLines
import org.thoughtcrime.securesms.contactshare.displayText
import org.thoughtcrime.securesms.contactshare.labelText
import org.thoughtcrime.securesms.contactshare.resolveSignalRecipient
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.IOException
import java.util.Locale

/** Reads the contact being shared, maps it to state, and trims it to the selection on send. */
class ShareContactRepository(
  private val context: Context = AppDependencies.application,
  private val locale: Locale = Locale.getDefault(),
  private val reader: ContactCardReader = ContactCardReader(context)
) {

  companion object {
    private val TAG = Log.tag(ShareContactRepository::class)
  }

  suspend fun load(source: SharedContactSource, recipientId: RecipientId?): LoadedContact? = withContext(SignalDispatchers.IO) {
    val contact = when (source) {
      is SharedContactSource.AddressBook -> reader.readSystemContact(source.contactUri)
      is SharedContactSource.SystemPhone -> reader.readSystemPhone(source.dataUri)
      is SharedContactSource.SignalContact -> Recipient.resolved(source.recipientId).toSharedContact()
      is SharedContactSource.VCard -> reader.readVCard(source.uri)
    } ?: return@withContext null

    val sendingTo = recipientId?.let { Recipient.resolved(it).getDisplayName(context) } ?: ""

    toLoadedContact(contact, sendingTo, recipientId, (source as? SharedContactSource.AddressBook)?.recipientId)
  }

  /**
   * Builds a shareable card for a Signal connection that has no address book entry.
   *
   * Everything comes from the profile, so there are no emails, addresses, or organization to offer.
   * A card with no name cannot be rendered by the receiver, so a recipient without one has nothing
   * worth sharing and yields null.
   */
  private fun Recipient.toSharedContact(): Contact? {
    val name = Contact.Name(
      profileName.givenName.nullIfBlank(),
      profileName.familyName.nullIfBlank(),
      null,
      null,
      null,
      null
    )

    if (name.givenName.isNullOrBlank() && name.familyName.isNullOrBlank()) {
      Log.w(TAG, "Recipient has no name to share.")
      return null
    }

    val phoneNumbers = e164
      .map { listOf(Contact.Phone(it, Contact.Phone.Type.MOBILE, null)) }
      .orElse(emptyList())

    val avatar = profilePhotoBlobUri(id)?.let { Contact.Avatar(it.toUri(), true) }

    return Contact(name, null, phoneNumbers, emptyList(), emptyList(), avatar).withSignalIdentity(this)
  }

  /**
   * Stamps the Signal identity of the person on the card onto it, so the receiver can reach them
   * without having to match on a phone number.
   *
   * The nickname and note are the sharer's own, private to them until they share the card. They are
   * carried because the receiver has no other way to learn what the sharer calls this person; a
   * recipient with neither leaves both absent.
   */
  private fun Contact.withSignalIdentity(recipient: Recipient): Contact {
    if (!RemoteConfig.contactSharingV2) {
      return this
    }

    val nickname = Contact.SignalNickname(
      recipient.nickname.givenName.nullIfBlank(),
      recipient.nickname.familyName.nullIfBlank()
    ).takeUnless { it.isEmpty }

    return Contact(
      this.name,
      this.organization,
      this.phoneNumbers,
      this.emails,
      this.postalAddresses,
      this.avatar,
      recipient.aci.orElse(null)?.takeIf { it.isValid }?.toString(),
      nickname,
      recipient.note.nullIfBlank()
    )
  }

  fun buildCard(contact: Contact, selection: ShareContactSelection): Contact {
    val parts = selection.name

    return Contact(
      parts?.toContactName() ?: Contact.Name.EMPTY_NAME,
      selectedOrganization(contact, selection),
      contact.phoneNumbers.selectedByIndex(selection.detailIds, PHONE_PREFIX),
      contact.emails.selectedByIndex(selection.detailIds, EMAIL_PREFIX),
      contact.postalAddresses.selectedByIndex(selection.detailIds, ADDRESS_PREFIX),
      selection.photo?.let { Contact.Avatar(it.uri.toUri(), it.isProfile) },
      contact.aci,
      contact.nickname?.takeIf { NICKNAME_ID in selection.detailIds },
      contact.note?.takeIf { NOTE_ID in selection.detailIds }
    )
  }

  /** A company that is the card's only name is always kept, since dropping it leaves nothing. */
  private fun selectedOrganization(contact: Contact, selection: ShareContactSelection): String? {
    val organization = selection.name?.organization.nullIfBlank() ?: contact.organization.nullIfBlank() ?: return null
    val isOnlyName = selection.name?.hasPersonalName != true
    val isOfferedAsRow = !isOnlyName && organization != ContactUtil.getDisplayName(contact)

    return if (!isOfferedAsRow || ORGANIZATION_ID in selection.detailIds) organization else null
  }

  private fun toLoadedContact(source: Contact, sendingTo: String, recipientId: RecipientId?, subject: RecipientId? = null): LoadedContact {
    val signalRecipient = subject ?: source.resolveSignalRecipient()

    // An address book card only learns who it is on Signal once its numbers have been looked up, so
    // the identity is stamped here rather than by the reader that built it.
    val contact = if (source.aci == null && signalRecipient != null) {
      source.withSignalIdentity(Recipient.resolved(signalRecipient))
    } else {
      source
    }

    val photoOptions = contact.resolvePhotoOptions(signalRecipient)
    val displayName = ContactUtil.getDisplayName(contact)

    return LoadedContact(
      contact = contact,
      photoOptions = photoOptions,
      state = ShareContactState(
        sendingTo = sendingTo,
        recipientId = recipientId,
        avatar = photoOptions.firstOrNull()?.let { option ->
          ShareContactState.AvatarSelection(
            isSelected = true,
            photo = option.photo,
            isEditable = photoOptions.size > 1
          )
        },
        name = ShareContactState.NameSelection(
          displayName = displayName,
          isSelected = true,
          isEditable = true,
          isToggleable = false
        ),
        details = contact.buildDetails(displayName)
      )
    )
  }

  private fun Contact.buildDetails(displayName: String): List<ShareContactState.DetailSelection> {
    val details = mutableListOf<ShareContactState.DetailSelection>()

    this.phoneNumbers.forEachIndexed { index, phone ->
      details += ShareContactState.DetailSelection(
        id = "$PHONE_PREFIX:$index",
        lines = listOf(ContactUtil.getPrettyPhoneNumber(phone, locale)),
        label = ShareContactState.DetailLabel.Text(phone.labelText(context)),
        isSelected = index == 0
      )
    }

    this.emails.forEachIndexed { index, email ->
      details += ShareContactState.DetailSelection(
        id = "$EMAIL_PREFIX:$index",
        lines = listOf(email.email),
        label = ShareContactState.DetailLabel.Text(email.labelText(context)),
        isSelected = false
      )
    }

    this.postalAddresses.forEachIndexed { index, address ->
      details += ShareContactState.DetailSelection(
        id = "$ADDRESS_PREFIX:$index",
        lines = address.displayLines(),
        label = ShareContactState.DetailLabel.Text(address.labelText(context)),
        isSelected = false
      )
    }

    this.organization.nullIfBlank()?.takeIf { it != displayName }?.let { company ->
      details += ShareContactState.DetailSelection(
        id = ORGANIZATION_ID,
        lines = listOf(company),
        label = ShareContactState.DetailLabel.Text(context.getString(R.string.ShareContactScreen__company)),
        isSelected = false
      )
    }

    // Unselected by default. Both are the sharer's own private annotations about this person, so
    // attaching one has to be a deliberate act rather than something that happens by not looking.
    this.nickname?.takeUnless { it.isEmpty }?.let { nickname ->
      details += ShareContactState.DetailSelection(
        id = NICKNAME_ID,
        lines = listOf(nickname.displayText()),
        label = ShareContactState.DetailLabel.Nickname,
        isSelected = false
      )
    }

    this.note.nullIfBlank()?.let { note ->
      details += ShareContactState.DetailSelection(
        id = NOTE_ID,
        lines = listOf(note),
        label = ShareContactState.DetailLabel.Note,
        isSelected = false
      )
    }

    return details
  }

  /**
   * Address book photo first per the design, Signal profile photo as the alternative, and sharing no
   * photo as the last choice. The last one is only offered when there is a photo to decline, since a
   * card with no photo has nothing to choose between.
   */
  private fun Contact.resolvePhotoOptions(signalRecipient: RecipientId?): List<ShareContactState.PhotoOption> {
    val options = mutableListOf<ShareContactState.PhotoOption>()
    val sharedAvatar = this.avatar

    if (sharedAvatar != null && !sharedAvatar.isProfile) {
      sharedAvatar.attachment?.uri?.let { uri ->
        options += ShareContactState.PhotoOption(
          id = PHOTO_ID_ADDRESS_BOOK,
          photo = ShareContactState.ContactPhoto(uri = uri.toString(), isProfile = false)
        )
      }
    }

    // A card built from a Signal profile already blobbed that photo, so blobbing again would offer
    // the same image twice.
    val alreadyBlobbed = sharedAvatar?.takeIf { it.isProfile }?.attachment?.uri?.toString()
    val profileUri = alreadyBlobbed ?: signalRecipient?.let { profilePhotoBlobUri(it) }

    if (profileUri != null) {
      options += ShareContactState.PhotoOption(
        id = PHOTO_ID_SIGNAL_PROFILE,
        photo = ShareContactState.ContactPhoto(uri = profileUri, isProfile = true)
      )
    }

    if (options.isNotEmpty()) {
      options += ShareContactState.PhotoOption(id = PHOTO_ID_NONE, photo = null)
    }

    return options
  }

  /** Blobs the stored profile avatar so it uploads as an ordinary attachment. */
  private fun profilePhotoBlobUri(recipientId: RecipientId): String? {
    if (!AvatarHelper.hasAvatar(context, recipientId)) {
      return null
    }

    return try {
      val bytes = AvatarHelper.getAvatarBytes(context, recipientId) ?: return null
      AppDependencies.blobs.forData(bytes).createForSingleSessionOnDisk(context).toString()
    } catch (e: IOException) {
      Log.w(TAG, "Could not read the profile avatar to share.", e)
      null
    }
  }

  private fun ContactNameParts.toContactName(): Contact.Name {
    return Contact.Name(
      this.givenName.nullIfBlank(),
      this.familyName.nullIfBlank(),
      this.prefix.nullIfBlank(),
      this.suffix.nullIfBlank(),
      this.middleName.nullIfBlank(),
      null
    )
  }

  private fun <T> List<T>.selectedByIndex(selectedIds: Set<String>, prefix: String): List<T> {
    return filterIndexed { index, _ -> "$prefix:$index" in selectedIds }
  }
}
