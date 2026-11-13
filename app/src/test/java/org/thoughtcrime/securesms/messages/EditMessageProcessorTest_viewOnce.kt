/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.EditMessage
import org.whispersystems.signalservice.internal.push.Envelope

/**
 * View-once messages can't be edited, so an edit must never be able to turn a regular message into one.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EditMessageProcessorTest_viewOnce {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var senderId: RecipientId

  @Before
  fun setUp() {
    every { RemoteConfig.regularDeleteThreshold } returns 86_400L

    senderId = recipients.createRecipient("Sender Name")
  }

  @Test
  fun `media edit flagged view-once is applied without the flag`() {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = 1000,
      serverTimeMillis = 1000,
      receivedTimeMillis = System.currentTimeMillis(),
      body = "original message"
    )
    SignalDatabase.messages.insertMessageInbox(message).get()

    EditMessageProcessor.process(
      context = ApplicationProvider.getApplicationContext(),
      senderRecipient = Recipient.resolved(senderId),
      threadRecipient = Recipient.resolved(senderId),
      envelope = Envelope(clientTimestamp = 2000, serverTimestamp = 2000),
      content = Content(
        editMessage = EditMessage(
          targetSentTimestamp = 1000,
          dataMessage = DataMessage(
            body = "edited message",
            timestamp = 2000,
            attachments = listOf(AttachmentPointer(contentType = "image/jpeg")),
            isViewOnce = true
          )
        )
      ),
      metadata = EnvelopeMetadata(
        sourceServiceId = Recipient.resolved(senderId).requireAci(),
        sourceE164 = null,
        sourceDeviceId = 1,
        sealedSender = true,
        groupId = null,
        destinationServiceId = recipients.selfAci,
        ciphertextMessageType = 0
      ),
      earlyMessageCacheEntry = null,
      batchCache = OneTimeBatchCache()
    )

    val revision = SignalDatabase.messages.getMessageFor(2000, senderId)
    assertThat(revision).isNotNull()
    assertThat(revision!!.body).isEqualTo("edited message")
    assertThat(revision.isViewOnce).isFalse()
  }
}
