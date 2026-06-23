/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import android.app.Application
import androidx.core.content.contentValuesOf
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.PNI
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.StorySend
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactSearchPagedDataSourceTest_letterHeaders {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `letter header lands on registered contact even when an unregistered system contact outranks it alphabetically`() {
    recipients.createRecipient("Alice Anderson")
    val charlieId = recipients.createRecipient("Charlie Chaplin")
    insertUnregisteredSystemContact("Carrolyn")

    val dataSource = ContactSearchPagedDataSource(
      contactConfiguration = ContactSearchConfiguration.build {
        addSection(
          ContactSearchConfiguration.Section.Individuals(
            includeHeader = false,
            includeSelfMode = RecipientTable.IncludeSelfMode.Exclude,
            includeLetterHeaders = true,
            transportType = ContactSearchConfiguration.TransportType.ALL
          )
        )
      },
      contactSearchPagedDataSourceRepository = object : ContactSearchPagedDataSourceRepository(ApplicationProvider.getApplicationContext()) {
        override fun getLatestStorySends(activeStoryCutoffDuration: Long): List<StorySend> = emptyList()
      }
    )

    val totalSize = dataSource.size()
    val rows = dataSource.load(0, totalSize, totalSize, PagedDataSource.CancellationSignal { false })

    val charlie = rows.filterIsInstance<ContactSearchData.KnownRecipient>()
      .firstOrNull { it.recipient.id == charlieId }
    assertNotNull("Charlie should be in the visible list. rows=$rows", charlie)
    assertEquals(
      "Charlie (registered) must carry the C header even though Carrolyn (unregistered system contact) sorts ahead of her. rows=$rows",
      "C",
      charlie!!.headerLetter
    )
  }

  @Test
  fun `each letter group yields exactly one header anchored to its first contact`() {
    val aaron = recipients.createRecipient("Aaron")
    val bella = recipients.createRecipient("Bella")
    val carol = recipients.createRecipient("Carol")
    val carrie = recipients.createRecipient("Carrie")
    val casey = recipients.createRecipient("Casey")
    val dana = recipients.createRecipient("Dana")

    val dataSource = buildIndividualsDataSource()
    val size = dataSource.size()
    val rows = dataSource.load(0, size, size, PagedDataSource.CancellationSignal { false })

    // Single-contact groups always carry their own header.
    assertEquals("A", rows.headerFor(aaron))
    assertEquals("B", rows.headerFor(bella))
    assertEquals("D", rows.headerFor(dana))

    // The three-contact C group must contribute exactly one "C" header (on whichever sorts first),
    // not one per contact.
    val cHeaders = listOf(carol, carrie, casey).mapNotNull { rows.headerFor(it) }
    assertEquals("exactly one C header across the C group", listOf("C"), cHeaders)
  }

  @Test
  fun `a letter group split across two page loads still yields a single header on the first page`() {
    val aaron = recipients.createRecipient("Aaron")
    val bella = recipients.createRecipient("Bella")
    val carol = recipients.createRecipient("Carol")
    val carrie = recipients.createRecipient("Carrie")
    val casey = recipients.createRecipient("Casey")
    val dana = recipients.createRecipient("Dana")

    val dataSource = buildIndividualsDataSource()
    val size = dataSource.size()

    // Sorted order is [Aaron(A), Bella(B), C, C, C, Dana(D)], so loading in two pages of three splits
    // the C group: one C ends the first page, two C's begin the second.
    val firstPage = dataSource.load(0, 3, size, PagedDataSource.CancellationSignal { false })
    val secondPage = dataSource.load(3, 3, size, PagedDataSource.CancellationSignal { false })

    val cContacts = listOf(carol, carrie, casey)
    val firstPageCHeaders = cContacts.mapNotNull { firstPage.knownRecipient(it)?.headerLetter }
    val secondPageCHeaders = cContacts.mapNotNull { secondPage.knownRecipient(it)?.headerLetter }

    // The single C header lands on the first contact of the run (first page); the C's that begin the
    // second page must NOT repeat it.
    assertEquals("C header belongs to the first contact of the run, on the first page", listOf("C"), firstPageCHeaders)
    assertEquals("the second page continues the C group and must not repeat the header", emptyList<String>(), secondPageCHeaders)
    assertEquals("D", secondPage.headerFor(dana))

    // Sanity: the C group genuinely straddled the page boundary.
    assertEquals("one C contact on the first page", 1, cContacts.count { firstPage.knownRecipient(it) != null })
    assertEquals("two C contacts on the second page", 2, cContacts.count { secondPage.knownRecipient(it) != null })
  }

  @Test
  fun `letter header reflects the sort name, not the display name, when a nickname diverges from the profile name`() {
    recipients.createRecipient("Bella")
    val mikeSortsAsCharlie = recipients.createRecipient("Mike")
    SignalDatabase.recipients.setNicknameAndNote(mikeSortsAsCharlie, ProfileName.fromParts("Charlie", null), "")
    recipients.createRecipient("Dan")

    val dataSource = buildIndividualsDataSource()
    val size = dataSource.size()
    val rows = dataSource.load(0, size, size, PagedDataSource.CancellationSignal { false })

    // This contact's nickname ("Charlie") sorts it among the C's even though its profile name is "Mike".
    // The header must follow the sort key (C), never the display name (M).
    assertEquals("header follows the sort key, not the display name", "C", rows.headerFor(mikeSortsAsCharlie))

    // End to end: headers march B, C, D in order, with no stray "M" leaking out of the display name.
    val headers = rows.filterIsInstance<ContactSearchData.KnownRecipient>().mapNotNull { it.headerLetter }
    assertEquals(listOf("B", "C", "D"), headers)
  }

  private fun buildIndividualsDataSource(): ContactSearchPagedDataSource {
    return ContactSearchPagedDataSource(
      contactConfiguration = ContactSearchConfiguration.build {
        addSection(
          ContactSearchConfiguration.Section.Individuals(
            includeHeader = false,
            includeSelfMode = RecipientTable.IncludeSelfMode.Exclude,
            includeLetterHeaders = true,
            transportType = ContactSearchConfiguration.TransportType.ALL
          )
        )
      },
      contactSearchPagedDataSourceRepository = object : ContactSearchPagedDataSourceRepository(ApplicationProvider.getApplicationContext()) {
        override fun getLatestStorySends(activeStoryCutoffDuration: Long): List<StorySend> = emptyList()
      }
    )
  }

  private fun List<ContactSearchData>.knownRecipient(id: RecipientId): ContactSearchData.KnownRecipient? {
    return filterIsInstance<ContactSearchData.KnownRecipient>().firstOrNull { it.recipient.id == id }
  }

  private fun List<ContactSearchData>.headerFor(id: RecipientId): String? {
    val match = knownRecipient(id) ?: error("Recipient $id not present in $this")
    return match.headerLetter
  }

  private fun insertUnregisteredSystemContact(name: String): RecipientId {
    val rowId = SignalDatabase.recipients.writableDatabase.insertOrThrow(
      RecipientTable.TABLE_NAME,
      null,
      contentValuesOf(
        RecipientTable.TYPE to 0,
        RecipientTable.E164 to "+15555550101",
        RecipientTable.ACI_COLUMN to null,
        RecipientTable.PNI_COLUMN to PNI.from(UUID.randomUUID()).toString(),
        RecipientTable.REGISTERED to RecipientTable.RegisteredState.NOT_REGISTERED.id,
        RecipientTable.PROFILE_SHARING to 1,
        RecipientTable.SYSTEM_GIVEN_NAME to name,
        RecipientTable.SYSTEM_JOINED_NAME to name,
        RecipientTable.SYSTEM_CONTACT_URI to "content://com.android.contacts/contacts/lookup/abc/1",
        RecipientTable.AVATAR_COLOR to "A110",
        RecipientTable.MESSAGE_EXPIRATION_TIME_VERSION to 1
      )
    )
    return RecipientId.from(rowId)
  }
}
