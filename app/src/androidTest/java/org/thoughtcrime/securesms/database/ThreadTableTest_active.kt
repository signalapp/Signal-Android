/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import java.util.UUID

@Suppress("ClassName")
class ThreadTableTest_active {

  @Rule
  @JvmField
  val databaseRule = SignalDatabaseRule()

  private lateinit var recipient: Recipient

  @Before
  fun setUp() {
    recipient = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID())))
  }

  @Test
  fun givenActiveUnarchivedThread_whenIGetUnarchivedConversationList_thenIExpectThread() {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, false)

    SignalDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10
    ).use { threads ->
      assertEquals(1, threads.count)

      val record = ThreadTable.StaticReader(threads, InstrumentationRegistry.getInstrumentation().context).getNext()

      assertNotNull(record)
      assertEquals(record!!.recipient.id, recipient.id)
    }
  }

  @Test
  fun givenInactiveUnarchivedThread_whenIGetUnarchivedConversationList_thenIExpectNoThread() {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, false)
    SignalDatabase.threads.deleteConversation(threadId)

    SignalDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10
    ).use { threads ->
      assertEquals(0, threads.count)
    }

    val threadId2 = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    assertEquals(threadId2, threadId)
  }

  @Test
  fun givenActiveArchivedThread_whenIGetUnarchivedConversationList_thenIExpectNoThread() {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, false)
    SignalDatabase.threads.setArchived(setOf(threadId), true)

    SignalDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10
    ).use { threads ->
      assertEquals(0, threads.count)
    }
  }

  @Test
  fun givenActiveArchivedThread_whenIGetArchivedConversationList_thenIExpectThread() {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, false)
    SignalDatabase.threads.setArchived(setOf(threadId), true)

    SignalDatabase.threads.getArchivedConversationList(
      ConversationFilter.OFF,
      0,
      10
    ).use { threads ->
      assertEquals(1, threads.count)
    }
  }

  @Test
  fun givenInactiveArchivedThread_whenIGetArchivedConversationList_thenIExpectNoThread() {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, false)
    SignalDatabase.threads.deleteConversation(threadId)
    SignalDatabase.threads.setArchived(setOf(threadId), true)

    SignalDatabase.threads.getArchivedConversationList(
      ConversationFilter.OFF,
      0,
      10
    ).use { threads ->
      assertEquals(0, threads.count)
    }

    val threadId2 = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    assertEquals(threadId2, threadId)
  }

  @Test
  fun givenActiveArchivedThread_whenIDeactivateThread_thenIExpectNoMessages() {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, false)

    SignalDatabase.messages.getConversation(threadId).use {
      assertEquals(1, it.count)
    }

    SignalDatabase.threads.deleteConversation(threadId)

    SignalDatabase.messages.getConversation(threadId).use {
      assertEquals(0, it.count)
    }
  }
}
