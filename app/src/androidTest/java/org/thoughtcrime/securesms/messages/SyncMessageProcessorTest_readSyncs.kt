/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class SyncMessageProcessorTest_readSyncs {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)
  }

  @After
  fun tearDown() {
    messageHelper.tearDown()
  }

  @Test
  fun handleSynchronizeReadMessage() {
    val message1Timestamp = messageHelper.incomingText().timestamp
    val message2Timestamp = messageHelper.incomingText().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(messageHelper.alice to message1Timestamp, messageHelper.alice to message2Timestamp)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }

  @Test
  fun handleSynchronizeReadMessageMissingTimestamp() {
    messageHelper.incomingText().timestamp
    val message2Timestamp = messageHelper.incomingText().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(messageHelper.alice to message2Timestamp)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }

  @Test
  fun handleSynchronizeReadWithEdits() {
    val message1Timestamp = messageHelper.incomingText().timestamp
    messageHelper.syncReadMessage(messageHelper.alice to message1Timestamp)

    val editMessage1Timestamp1 = messageHelper.incomingEditText(message1Timestamp).timestamp
    val editMessage1Timestamp2 = messageHelper.incomingEditText(editMessage1Timestamp1).timestamp

    val message2Timestamp = messageHelper.incomingMedia().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(messageHelper.alice to message2Timestamp, messageHelper.alice to editMessage1Timestamp1, messageHelper.alice to editMessage1Timestamp2)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }

  @Test
  fun handleSynchronizeReadWithEditsInGroup() {
    val message1Timestamp = messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp

    messageHelper.syncReadMessage(messageHelper.alice to message1Timestamp)

    val editMessage1Timestamp1 = messageHelper.incomingEditText(targetTimestamp = message1Timestamp, sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp
    val editMessage1Timestamp2 = messageHelper.incomingEditText(targetTimestamp = editMessage1Timestamp1, sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp

    val message2Timestamp = messageHelper.incomingMedia(sender = messageHelper.bob, destination = messageHelper.group.recipientId).timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(messageHelper.bob to message2Timestamp, messageHelper.alice to editMessage1Timestamp1, messageHelper.alice to editMessage1Timestamp2)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }
}
