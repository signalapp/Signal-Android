/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Envelope
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DataMessageProcessorTest_pollCreate {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var alice: Recipient

  @Before
  fun setUp() {
    alice = Recipient.resolved(recipients.createRecipient("Alice"))
  }

  @Test
  fun handlePollCreate_whenOptionIsWithinGraphemeLimitButLongerInUtf16_createPoll() {
    // 60 graphemes, 120 UTF-16 code units. Desktop and iOS count poll limits in graphemes, so they can send this.
    val option = "👍".repeat(60)

    val insertResult = handlePollCreate(DataMessage.PollCreate(question = "question?", options = listOf(option, "b"), allowMultiple = false))

    assertThat(insertResult).isNotNull()
    val poll = SignalDatabase.polls.getPoll(insertResult!!.messageId)
    assertThat(poll).isNotNull()
    assertThat(poll!!.pollOptions.size).isEqualTo(2)
  }

  @Test
  fun handlePollCreate_whenQuestionIsWithinGraphemeLimitButLongerInUtf16_createPoll() {
    // 150 graphemes, 300 UTF-16 code units.
    val question = "é".repeat(150)

    val insertResult = handlePollCreate(DataMessage.PollCreate(question = question, options = listOf("a", "b"), allowMultiple = false))

    assertThat(insertResult).isNotNull()
  }

  @Test
  fun handlePollCreate_whenOptionExceedsGraphemeLimit_dropMessage() {
    val insertResult = handlePollCreate(DataMessage.PollCreate(question = "question?", options = listOf("a".repeat(101), "b"), allowMultiple = false))

    assertThat(insertResult).isNull()
  }

  @Test
  fun handlePollCreate_whenQuestionExceedsGraphemeLimit_dropMessage() {
    val insertResult = handlePollCreate(DataMessage.PollCreate(question = "q".repeat(201), options = listOf("a", "b"), allowMultiple = false))

    assertThat(insertResult).isNull()
  }

  private fun handlePollCreate(pollCreate: DataMessage.PollCreate): MessageTable.InsertResult? {
    return DataMessageProcessor.handlePollCreate(
      context = ApplicationProvider.getApplicationContext(),
      envelope = Envelope(clientTimestamp = 1000, serverTimestamp = 1001, serverGuid = UUID.randomUUID().toString()),
      metadata = EnvelopeMetadata(alice.requireServiceId(), null, 1, false, null, Recipient.self().requireServiceId(), CiphertextMessage.WHISPER_TYPE),
      message = DataMessage(pollCreate = pollCreate),
      senderRecipient = alice,
      threadRecipient = alice,
      groupId = null,
      receivedTime = 0
    )
  }
}
