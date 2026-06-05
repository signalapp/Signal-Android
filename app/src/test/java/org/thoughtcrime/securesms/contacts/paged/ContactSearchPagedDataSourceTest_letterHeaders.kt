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
