/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import android.Manifest
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteFullException
import android.provider.ContactsContract
import kotlinx.coroutines.CancellationException
import org.signal.contacts.SystemContactsRepository
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.requireInt
import org.signal.core.util.requireLongOrNull
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.util.SignalE164Util

/**
 * Populates a [ContactIndexDatabase] from the two sources that make up the merged contact list.
 *
 * The overlap between them is resolved by lookup key rather than by phone number, which is what lets
 * this avoid reading the Data table entirely. One query over registered recipients that carry a
 * system contact URI gives us every address book entry that is also on Signal, bounded by Signal
 * contact count rather than by address book size.
 */
class ContactIndexBuilder(private val context: Context) {

  companion object {
    private val TAG = Log.tag(ContactIndexBuilder::class.java)

    /** Caps how many entries are in memory at once, so an index of any size stays bounded. */
    private const val BATCH_SIZE = 500
  }

  fun build(database: ContactIndexDatabase): ContactIndexBuildResult {
    val stopwatch = Stopwatch("contact-index")

    return try {
      val hasPermission = Permissions.hasAll(context, Manifest.permission.READ_CONTACTS)

      val links = SignalDatabase.recipients.getSystemContactLinks()
      val signalOnly: List<RecipientTable.SignalOnlyContact> = SignalDatabase.recipients.getSignalOnlyContactsForIndex(excludeSystemContacts = hasPermission)
      stopwatch.split("recipients")

      val sortKeys = ContactSortKeyGenerator()

      var signalOnlyIndexed = 0
      var addressBook = InsertCount()

      val count = database.withinBuild {
        signalOnlyIndexed = insertSignalOnly(database, signalOnly, sortKeys)
        stopwatch.split("signal-only")

        if (hasPermission) {
          addressBook = insertAddressBook(database, links, sortKeys)
          stopwatch.split("address-book")
        } else {
          Log.i(TAG, "No contacts permission. Indexing Signal contacts only.")
        }
      }
      stopwatch.split("sort")
      stopwatch.stop(TAG)

      Log.i(
        TAG,
        "Indexed $count rows. signalOnly=$signalOnlyIndexed/${signalOnly.size} addressBook=${addressBook.inserted}/${addressBook.inserted + addressBook.skipped + addressBook.invalidId} invalidId=${addressBook.invalidId} linkedToSignal=${links.size}"
      )

      if (hasPermission) ContactIndexBuildResult.Success(count) else ContactIndexBuildResult.SignalOnly(count)
    } catch (e: CancellationException) {
      throw e
    } catch (e: SQLiteFullException) {
      Log.w(TAG, "Not enough space to build the contact index.", e)
      ContactIndexBuildResult.OutOfSpace
    } catch (e: Exception) {
      Log.w(TAG, "Failed to build the contact index.", e)
      ContactIndexBuildResult.Failure(e)
    }
  }

  private fun insertSignalOnly(
    database: ContactIndexDatabase,
    contacts: List<RecipientTable.SignalOnlyContact>,
    sortKeys: ContactSortKeyGenerator
  ): Int {
    var inserted = 0

    contacts
      .asSequence()
      .mapNotNull { it.toEntry(sortKeys) }
      .chunked(BATCH_SIZE)
      .forEach {
        database.insert(it)
        inserted += it.size
      }

    return inserted
  }

  private fun insertAddressBook(
    database: ContactIndexDatabase,
    systemContactLinks: List<RecipientTable.SystemContactLink>,
    sortKeys: ContactSortKeyGenerator
  ): InsertCount {
    val cursor: Cursor = SystemContactsRepository.getAllContactsForList(context) ?: run {
      Log.w(TAG, "Contacts provider returned no cursor.")
      return InsertCount()
    }

    var inserted = 0
    var skipped = 0
    var invalidId = 0

    cursor.use {
      val links = SystemContactLinks(systemContactLinks, it.currentLookupKeys())

      val batch = ArrayList<ContactIndexEntry>(BATCH_SIZE)

      while (it.moveToNext()) {
        val contactId = it.requireLongOrNull(ContactsContract.Contacts._ID)

        if (contactId == null || contactId <= 0) {
          invalidId++
          continue
        }

        val entries = it.toEntries(contactId, links, sortKeys)

        if (entries.isEmpty()) {
          skipped++
          continue
        }

        batch += entries

        if (batch.size >= BATCH_SIZE) {
          database.insert(batch)
          inserted += batch.size
          batch.clear()
        }
      }

      database.insert(batch)
      inserted += batch.size
    }

    return InsertCount(inserted, skipped, invalidId)
  }

  /** Leaves the cursor before its first row, ready for the pass that inserts. */
  private fun Cursor.currentLookupKeys(): List<String> {
    val keys = ArrayList<String>(count)

    while (moveToNext()) {
      requireString(ContactsContract.Contacts.LOOKUP_KEY)?.let { keys += it }
    }

    moveToPosition(-1)
    return keys
  }

  /** Rows that made it into the index versus rows the provider gave us that had nothing to show. */
  private data class InsertCount(val inserted: Int = 0, val skipped: Int = 0, val invalidId: Int = 0)

  private fun RecipientTable.SignalOnlyContact.toEntry(sortKeys: ContactSortKeyGenerator): ContactIndexEntry? {
    val displayName = ContactDisplayName.forSignalContact(
      nickname = nickname,
      systemName = systemName,
      profileName = profileName,
      username = username,
      e164 = e164,
      email = email
    ) ?: return null

    return ContactIndexEntry(
      sortKey = sortKeys.of(displayName),
      type = ContactIndexType.SIGNAL_ONLY,
      section = ContactDisplayName.sectionFor(displayName, hasPersonalName = true, hasNickname = nickname != null),
      displayName = displayName,
      searchText = ContactDisplayName.searchTextOf(nickname, systemName, profileName, username, e164, email),
      recipientId = recipientId
    )
  }

  /** One entry per registered recipient, since two numbers on one contact are two Signal accounts. */
  private fun Cursor.toEntries(
    contactId: Long,
    links: SystemContactLinks,
    sortKeys: ContactSortKeyGenerator
  ): List<ContactIndexEntry> {
    val lookupKey = requireString(ContactsContract.Contacts.LOOKUP_KEY) ?: return emptyList()
    val matched = links.find(lookupKey) {
      SystemContactsRepository.getDisplayNumbersForContact(context, contactId).mapNotNull { SignalE164Util.formatAsE164(it) }.toSet()
    }

    return if (matched.isEmpty()) {
      listOfNotNull(toEntry(contactId, lookupKey, null, sortKeys))
    } else {
      matched.mapNotNull { toEntry(contactId, lookupKey, it, sortKeys) }
    }
  }

  private fun Cursor.toEntry(
    contactId: Long,
    lookupKey: String,
    link: RecipientTable.SystemContactLink?,
    sortKeys: ContactSortKeyGenerator
  ): ContactIndexEntry? {
    val providerName = requireString(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

    // A contact with no name at all cannot be rendered or shared, so it is left out rather than
    // shown as a blank row.
    val displayName = ContactDisplayName.forSystemContact(providerName, link?.nickname) ?: return null

    val displayNameSource = requireInt(ContactsContract.Contacts.DISPLAY_NAME_SOURCE)
    val hasPersonalName = SystemContactsRepository.isPersonalDisplayName(displayNameSource)

    return ContactIndexEntry(
      sortKey = sortKeys.of(displayName),
      type = if (link != null) ContactIndexType.BOTH else ContactIndexType.SYSTEM_ONLY,
      section = ContactDisplayName.sectionFor(displayName, hasPersonalName, hasNickname = link?.nickname != null),
      displayName = displayName,
      searchText = ContactDisplayName.searchTextOf(providerName, link?.nickname),
      recipientId = link?.recipientId,
      lookupKey = lookupKey,
      contactId = contactId,
      hasPersonalName = hasPersonalName,
      isOrgOnly = SystemContactsRepository.isOrganizationDisplayName(displayNameSource) && link?.nickname == null,
      hasPhoto = requireString(ContactsContract.Contacts.PHOTO_URI) != null
    )
  }
}
