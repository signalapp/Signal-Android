/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalActivityRule

/**
 * An incoming edit inherits its target's date_received and is inserted unread, so the conversation's mark-read
 * watermark never advances past it. These cover marking the revision read at insert time when the thread is already
 * on screen, so a read receipt isn't deferred until the next message happens to arrive.
 */
@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class EditMessageProcessorTest_markRead {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)
  }

  @After
  fun tearDown() {
    AppDependencies.messageNotifier.clearVisibleThread()
    messageHelper.tearDown()
  }

  @Test
  fun givenVisibleThread_whenEditArrives_thenRevisionIsRead() {
    val originalTimestamp = messageHelper.incomingText().timestamp
    messageHelper.syncReadMessage(messageHelper.alice to originalTimestamp)

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    AppDependencies.messageNotifier.setVisibleThread(ConversationId.forConversation(threadId))

    val editTimestamp = messageHelper.incomingEditText(originalTimestamp).timestamp

    val revision = SignalDatabase.messages.getMessageFor(editTimestamp, messageHelper.alice) as MmsMessageRecord?
    assertThat(revision).isNotNull()
    assertThat(revision!!.isRead).isEqualTo(true)
  }

  @Test
  fun givenNoVisibleThread_whenEditArrives_thenRevisionIsUnread() {
    val originalTimestamp = messageHelper.incomingText().timestamp
    messageHelper.syncReadMessage(messageHelper.alice to originalTimestamp)

    AppDependencies.messageNotifier.clearVisibleThread()

    val editTimestamp = messageHelper.incomingEditText(originalTimestamp).timestamp

    val revision = SignalDatabase.messages.getMessageFor(editTimestamp, messageHelper.alice) as MmsMessageRecord?
    assertThat(revision).isNotNull()
    assertThat(revision!!.isRead).isEqualTo(false)
  }

  @Test
  fun givenVisibleOtherThread_whenEditArrives_thenRevisionIsUnread() {
    val originalTimestamp = messageHelper.incomingText().timestamp
    messageHelper.syncReadMessage(messageHelper.alice to originalTimestamp)

    val otherThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(messageHelper.bob))
    AppDependencies.messageNotifier.setVisibleThread(ConversationId.forConversation(otherThreadId))

    val editTimestamp = messageHelper.incomingEditText(originalTimestamp).timestamp

    val revision = SignalDatabase.messages.getMessageFor(editTimestamp, messageHelper.alice) as MmsMessageRecord?
    assertThat(revision).isNotNull()
    assertThat(revision!!.isRead).isEqualTo(false)
  }
}
