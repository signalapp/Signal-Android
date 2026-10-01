/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.contacts.SystemContactsRepository
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord
import org.thoughtcrime.securesms.contacts.index.ContactIndexType
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactIndexSourceTest {

  companion object {
    private const val LOOKUP_KEY = "lookup-1"
    private const val CONTACT_ID = 1L
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @After
  fun tearDown() {
    unmockkStatic(SystemContactsRepository::class)
  }

  /**
   * Contacts permission can go away after the index was built, which leaves rows whose address book half is
   * no longer readable. Those still have a Signal profile to share, so they degrade rather than throw.
   */
  @Test
  fun `losing contacts permission degrades an address book row to its signal profile`() = runTest(testDispatcher) {
    val resolved = sourceThatLostPermission().resolve(addressBookRecord(recipientId = RecipientId.from(7)))

    assertThat(resolved).isEqualTo(SharedContactSource.SignalContact(RecipientId.from(7)))
  }

  @Test
  fun `losing contacts permission on a row with no signal profile resolves to nothing`() = runTest(testDispatcher) {
    val resolved = sourceThatLostPermission().resolve(addressBookRecord(recipientId = null))

    assertThat(resolved).isNull()
  }

  /**
   * Stubbed with a concrete context rather than `any()`, because mockk runs the real call once while it
   * records, and the dummy it fabricates for `any()` cannot answer `getContentResolver()`.
   */
  private fun sourceThatLostPermission(): ContactIndexSource {
    val context = RuntimeEnvironment.getApplication()

    mockkStatic(SystemContactsRepository::class)
    every { SystemContactsRepository.currentContactUri(context, LOOKUP_KEY, CONTACT_ID) } throws SecurityException("denied")

    return ContactIndexSource(mockk(relaxed = true), context)
  }

  private fun addressBookRecord(recipientId: RecipientId?): ContactIndexRecord {
    return ContactIndexRecord(
      position = 1,
      type = if (recipientId != null) ContactIndexType.BOTH else ContactIndexType.SYSTEM_ONLY,
      section = "A",
      displayName = "Andrew Bell",
      recipientId = recipientId,
      lookupKey = LOOKUP_KEY,
      contactId = CONTACT_ID,
      hasPersonalName = true,
      hasPhoto = false
    )
  }
}
