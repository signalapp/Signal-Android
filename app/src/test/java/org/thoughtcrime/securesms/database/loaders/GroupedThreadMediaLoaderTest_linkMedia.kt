/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.loaders

import android.app.Application
import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupedThreadMediaLoaderTest_linkMedia {

  @get:Rule
  val harness = RecipientTestRule()

  private lateinit var other: RecipientId
  private var threadId: Long = 0
  private var nextSentTime: Long = 1000

  @Before
  fun setUp() {
    other = harness.createRecipient("Other Person")
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(other))
  }

  @Test
  fun givenBodiesThatMatchTheQueryButHoldNoUsableLink_whenLoadingLinkMedia_thenOnlyTheRealLinkIsKept() {
    insertMessage("look at https://signal.org")
    insertMessage("https://localhost/admin")
    insertMessage("https://example.com")
    insertMessage("https://192.168.1.10")
    insertMessage("the string https:// on its own")

    val loaded = loadLinkMedia()

    assertThat(loaded.map { it.linkUrl }).containsExactly("https://signal.org")
  }

  private fun loadLinkMedia(): List<MediaTable.MediaRecord> {
    val loader = GroupedThreadMediaLoader(
      ApplicationProvider.getApplicationContext(),
      threadId,
      MediaLoader.MediaType.LINK,
      MediaTable.Sorting.Oldest,
      0
    )

    val grouped = requireNotNull(loader.loadInBackground())

    return buildList {
      for (section in 0 until grouped.sectionCount) {
        for (item in 0 until grouped.getSectionItemCount(section)) {
          add(grouped.get(section, item))
        }
      }
    }
  }

  private fun insertMessage(body: String): Long {
    val sentTime = nextSentTime++
    val values = ContentValues().apply {
      put(MessageTable.DATE_SENT, sentTime)
      put(MessageTable.DATE_RECEIVED, sentTime)
      put(MessageTable.THREAD_ID, threadId)
      put(MessageTable.FROM_RECIPIENT_ID, harness.self.serialize())
      put(MessageTable.TO_RECIPIENT_ID, other.serialize())
      put(MessageTable.TYPE, MessageTypes.BASE_SENT_TYPE or MessageTypes.PUSH_MESSAGE_BIT)
      put(MessageTable.BODY, body)
    }

    return SignalDatabase.writableDatabase.insert(MessageTable.TABLE_NAME, null, values)
  }
}
