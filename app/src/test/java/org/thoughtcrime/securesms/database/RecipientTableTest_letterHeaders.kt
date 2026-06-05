/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest_letterHeaders {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `letter header anchors are always in getSignalContacts`() {
    recipients.createRecipient("Alice Anderson")
    recipients.createRecipient("Bob Baker")
    recipients.createRecipient("Charlie Chaplin")
    recipients.createRecipient("David Dunn")

    assertHeaderAnchorsAreVisible()
  }

  @Test
  fun `hidden contact is not a letter header anchor`() {
    recipients.createRecipient("Alice Anderson")
    val hidden = recipients.createRecipient("Carrolyn Carter")
    SignalDatabase.recipients.markHidden(hidden)

    assertHeaderAnchorsAreVisible()
  }

  @Test
  fun `blocked contact is not a letter header anchor`() {
    recipients.createRecipient("Alice Anderson")
    val blocked = recipients.createRecipient("Carrolyn Carter")
    SignalDatabase.recipients.setBlocked(blocked, true)

    assertHeaderAnchorsAreVisible()
  }

  @Test
  fun `every visible letter section has a header anchor`() {
    recipients.createRecipient(ProfileName.fromParts("Alice", "Anderson"))
    recipients.createRecipient(ProfileName.fromParts("Bob", "Baker"))
    recipients.createRecipient(ProfileName.fromParts("Charlie", "Chaplin"))
    SignalDatabase.recipients.setSystemContactName(recipients.createRecipient(ProfileName.fromParts("Dave", "Dunn")), "Dave Dunn")

    val visibleLetters: Set<String> = visibleSignalContacts().values
      .filter { it.isNotEmpty() }
      .mapNotNull { name -> name.firstOrNull()?.uppercaseChar()?.toString() }
      .toSet()

    val headerLetters: Set<String> = SignalDatabase.recipients.querySignalContactLetterHeaders(
      "",
      RecipientTable.IncludeSelfMode.Exclude,
      includePush = true,
      includeSms = false
    ).values.toSet()

    assertTrue(
      "Every visible letter must have a header anchor. visible=$visibleLetters headers=$headerLetters",
      visibleLetters.all { it in headerLetters }
    )
  }

  private fun assertHeaderAnchorsAreVisible() {
    val visibleIds = visibleSignalContactIds()
    val headers = SignalDatabase.recipients.querySignalContactLetterHeaders(
      "",
      RecipientTable.IncludeSelfMode.Exclude,
      includePush = true,
      includeSms = false
    )
    val orphaned = headers.keys - visibleIds
    assertTrue(
      "Header anchors must all appear in getSignalContacts. orphaned=$orphaned headers=$headers visible=$visibleIds",
      orphaned.isEmpty()
    )
  }

  private fun visibleSignalContactIds(): Set<RecipientId> {
    return SignalDatabase.recipients.getSignalContacts(RecipientTable.IncludeSelfMode.Exclude).use { cursor ->
      val ids = mutableSetOf<RecipientId>()
      while (cursor.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.ID)))
      }
      ids
    }
  }

  private fun visibleSignalContacts(): Map<RecipientId, String> {
    return SignalDatabase.recipients.getSignalContacts(RecipientTable.IncludeSelfMode.Exclude).use { cursor ->
      val rows = mutableMapOf<RecipientId, String>()
      while (cursor.moveToNext()) {
        val id = RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.ID))
        val systemName = CursorUtil.requireString(cursor, RecipientTable.SYSTEM_JOINED_NAME)
        val profileName = CursorUtil.requireString(cursor, RecipientTable.SEARCH_PROFILE_NAME)
        rows[id] = systemName ?: profileName ?: ""
      }
      rows
    }
  }
}
