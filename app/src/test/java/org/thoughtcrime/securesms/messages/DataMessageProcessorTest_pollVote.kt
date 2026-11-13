/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isNull
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.util.EarlyMessageCacheEntry
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Envelope
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DataMessageProcessorTest_pollVote {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var pollAuthor: Recipient
  private lateinit var voter: Recipient

  @Before
  fun setUp() {
    pollAuthor = Recipient.resolved(recipients.createRecipient("Alice"))
    voter = Recipient.resolved(recipients.createRecipient("Bob"))
  }

  @Test
  fun handlePollVote_whenPollHasNotArrivedYet_cacheVoteUnderPollAuthor() {
    val pollSentTimestamp = 500L
    val message = DataMessage(
      pollVote = DataMessage.PollVote(
        targetAuthorAciBinary = pollAuthor.requireAci().toByteString(),
        targetSentTimestamp = pollSentTimestamp,
        optionIndexes = listOf(0),
        voteCount = 1
      )
    )
    val envelope = Envelope(clientTimestamp = 1000, serverTimestamp = 1001, serverGuid = UUID.randomUUID().toString())
    val metadata = EnvelopeMetadata(voter.requireServiceId(), null, 1, false, null, Recipient.self().requireServiceId(), CiphertextMessage.WHISPER_TYPE)
    val earlyEntry = EarlyMessageCacheEntry(envelope, Content(dataMessage = message), metadata, 1002)
    val earlyMessageCache = AppDependencies.earlyMessageCache

    val result = DataMessageProcessor.handlePollVote(
      context = ApplicationProvider.getApplicationContext(),
      envelope = envelope,
      message = message,
      senderRecipient = voter,
      threadRecipient = voter,
      earlyMessageCacheEntry = earlyEntry,
      batchCache = OneTimeBatchCache()
    )

    assertThat(result).isNull()
    // The poll's arrival drains the cache under its own sender (the poll author), so the vote must be stored there.
    verify { earlyMessageCache.store(pollAuthor.id, pollSentTimestamp, earlyEntry) }
    verify(exactly = 0) { earlyMessageCache.store(voter.id, any(), any()) }
  }
}
