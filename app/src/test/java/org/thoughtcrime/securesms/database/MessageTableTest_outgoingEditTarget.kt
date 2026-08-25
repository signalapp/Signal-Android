/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.days

/**
 * Verifies that [MessageTable.getOutgoingMessage] reports the correct revision to edit, which is what the send jobs turn into the
 * `targetSentTimestamp` of the outgoing edit. This has to keep working even when a newer revision was created before the send job for an
 * earlier revision ran, which is what happens when a message is edited multiple times while offline.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_outgoingEditTarget {

  @get:Rule
  val recipients = RecipientTestRule()

  private val messages: MessageTable
    get() = SignalDatabase.messages

  private lateinit var recipientId: RecipientId
  private var threadId: Long = 0

  @Before
  fun setUp() {
    every { RemoteConfig.regularDeleteThreshold } returns 1.days.inWholeSeconds
    every { RemoteConfig.adminDeleteThreshold } returns 1.days.inWholeSeconds

    recipientId = recipients.createRecipient("Recipient Name")
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipientId, false, ThreadTable.DistributionTypes.DEFAULT)
  }

  @Test
  fun originalMessageIsNotAnEdit() {
    val originalId = insertOutgoing(time = 1000)

    assertThat(messages.getOutgoingMessage(originalId).messageToEdit).isEqualTo(0L)
  }

  @Test
  fun singleEditTargetsTheOriginal() {
    val originalId = insertOutgoing(time = 1000)
    val editId = insertEdit(messageToEdit = originalId, time = 1001)

    assertThat(messages.getOutgoingMessage(editId).messageToEdit).isEqualTo(originalId)
  }

  @Test
  fun secondEditTargetsTheFirstEdit() {
    val originalId = insertOutgoing(time = 1000)
    val edit1Id = insertEdit(messageToEdit = originalId, time = 1001)
    val edit2Id = insertEdit(messageToEdit = edit1Id, time = 1002)

    assertThat(messages.getOutgoingMessage(edit2Id).messageToEdit).isEqualTo(edit1Id)
  }

  @Test
  fun earlierRevisionStillTargetsItsPredecessorAfterALaterRevisionExists() {
    val originalId = insertOutgoing(time = 1000)
    val edit1Id = insertEdit(messageToEdit = originalId, time = 1001)
    insertEdit(messageToEdit = edit1Id, time = 1002)

    assertThat(messages.getOutgoingMessage(edit1Id).messageToEdit).isEqualTo(originalId)
  }

  @Test
  fun everyRevisionTargetsItsPredecessorAfterManyEdits() {
    val originalId = insertOutgoing(time = 1000)
    val edit1Id = insertEdit(messageToEdit = originalId, time = 1001)
    val edit2Id = insertEdit(messageToEdit = edit1Id, time = 1002)
    val edit3Id = insertEdit(messageToEdit = edit2Id, time = 1003)

    assertThat(messages.getOutgoingMessage(edit1Id).messageToEdit).isEqualTo(originalId)
    assertThat(messages.getOutgoingMessage(edit2Id).messageToEdit).isEqualTo(edit1Id)
    assertThat(messages.getOutgoingMessage(edit3Id).messageToEdit).isEqualTo(edit2Id)
  }

  @Test
  fun editsInOtherChainsAreIgnored() {
    val otherOriginalId = insertOutgoing(time = 500)
    insertEdit(messageToEdit = otherOriginalId, time = 501)

    val originalId = insertOutgoing(time = 1000)
    val editId = insertEdit(messageToEdit = originalId, time = 1001)

    assertThat(messages.getOutgoingMessage(editId).messageToEdit).isEqualTo(originalId)
  }

  private fun insertOutgoing(time: Long): Long {
    val message = OutgoingMessage.text(
      threadRecipient = Recipient.resolved(recipientId),
      body = "out $time",
      expiresIn = 0,
      sentTimeMillis = time
    )
    return messages.insertMessageOutbox(message, threadId).messageId
  }

  private fun insertEdit(messageToEdit: Long, time: Long): Long {
    val message = OutgoingMessage.editText(
      recipient = Recipient.resolved(recipientId),
      body = "edit $time",
      sentTimeMillis = time,
      bodyRanges = null,
      messageToEdit = messageToEdit
    )
    return messages.insertMessageOutbox(message, threadId).messageId
  }
}
