/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.network.service.GroupSendDestination
import org.signal.network.service.GroupSendRecipient
import org.signal.network.service.MessageService
import org.signal.network.service.SendRequest
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.internal.push.TypingMessage
import java.io.IOException
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class TypingSendJobTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val messageService: MessageService
    get() = AppDependencies.messageService

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId

  @Before
  fun setUp() {
    every { recipients.signalStore.settings.isTypingIndicatorsEnabled } returns true

    mockkStatic(SealedSenderAccessUtil::class)
    every { SealedSenderAccessUtil.getSealedSenderAccessFor(any<Recipient>()) } returns null
    every { SealedSenderAccessUtil.getAccessMapFor(any(), any()) } answers { firstArg<List<Recipient>>().associate { it.id to Optional.empty() } }

    coEvery { messageService.sendMessage(any(), any(), any(), any()) } returns MessageService.SendSuccess(mockk(), true, listOf(1)).right()
    coEvery { messageService.sendGroupMessage(any(), any(), any(), any()) } returns emptyList<MessageService.RecipientSendResult>().right()

    alice = recipients.createRecipient("Alice")
    bob = recipients.createRecipient("Bob")
  }

  @After
  fun tearDown() {
    unmockkStatic(SealedSenderAccessUtil::class)
  }

  @Test
  fun `an individual gets an online, non-urgent typing message`() {
    val request = slot<SendRequest<EnvelopeContent>>()
    val aliceServiceId = Recipient.resolved(alice).requireServiceId()

    val result = runJob(threadFor(alice), typing = true)

    assertThat(result.isSuccess).isTrue()
    coVerify { messageService.sendMessage(aliceServiceId, any(), capture(request), any()) }
    assertThat(request.captured.isOnline).isTrue()
    assertThat(request.captured.urgent).isFalse()
    val typingMessage = request.captured.content.content.get().typingMessage!!
    assertThat(typingMessage.action).isEqualTo(TypingMessage.Action.STARTED)
    assertThat(typingMessage.groupId).isNull()
  }

  @Test
  fun `a group gets an online, non-urgent group send without a message id`() {
    val group = recipients.createGroup(alice, bob)
    val request = slot<SendRequest<EnvelopeContent.Encrypted>>()
    val sentTo = slot<List<GroupSendRecipient>>()

    val result = runJob(threadFor(group.recipientId), typing = false)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      messageService.sendGroupMessage(
        destination = GroupSendDestination.Group(GroupIdentifier(group.groupId.decodedId)),
        recipients = capture(sentTo),
        request = capture(request),
        messageId = null
      )
    }
    assertThat(sentTo.captured.map { it.id }).containsExactlyInAnyOrder(alice.toLong(), bob.toLong())
    assertThat(request.captured.isOnline).isTrue()
    assertThat(request.captured.urgent).isFalse()
    assertThat(request.captured.content.contentHint).isEqualTo(ContentHint.IMPLICIT)
    val typingMessage = request.captured.content.content.get().typingMessage!!
    assertThat(typingMessage.action).isEqualTo(TypingMessage.Action.STOPPED)
    assertThat(typingMessage.groupId).isEqualTo(group.groupId.decodedId.toByteString())
  }

  @Test
  fun `a failed send still succeeds`() {
    coEvery { messageService.sendMessage(any(), any(), any(), any()) } returns MessageService.SendError.NetworkError(IOException()).left()

    assertThat(runJob(threadFor(alice), typing = true).isSuccess).isTrue()
  }

  @Test
  fun `nothing is sent when typing indicators are disabled`() {
    every { recipients.signalStore.settings.isTypingIndicatorsEnabled } returns false

    assertThat(runJob(threadFor(alice), typing = true).isSuccess).isTrue()
    verifyNothingSent()
  }

  @Test
  fun `nothing is sent to a blocked recipient`() {
    SignalDatabase.recipients.setBlocked(alice, true, System.currentTimeMillis())
    Recipient.live(alice).refresh()

    assertThat(runJob(threadFor(alice), typing = true).isSuccess).isTrue()
    verifyNothingSent()
  }

  @Test
  fun `nothing is sent to a group we are no longer in`() {
    val group = recipients.createGroup(alice, bob)
    SignalDatabase.groups.setMember(group.groupId, false)

    assertThat(runJob(threadFor(group.recipientId), typing = true).isSuccess).isTrue()
    verifyNothingSent()
  }

  @Test
  fun `nothing is sent for a missing thread`() {
    assertThat(runJob(threadId = 9999L, typing = true).isSuccess).isTrue()
    verifyNothingSent()
  }

  @Test
  fun `fails without sending when we are not registered`() {
    every { recipients.signalStore.account.isRegistered } returns false
    Recipient.live(recipients.self).refresh()

    assertThat(runJob(threadFor(alice), typing = true).isFailure).isTrue()
    verifyNothingSent()
  }

  private fun threadFor(id: RecipientId): Long = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(id))

  private fun runJob(threadId: Long, typing: Boolean): Job.Result {
    val job = TypingSendJob(threadId, typing)
    job.setContext(ApplicationProvider.getApplicationContext())
    return job.run()
  }

  private fun verifyNothingSent() {
    coVerify(exactly = 0) { messageService.sendMessage(any(), any(), any(), any()) }
    coVerify(exactly = 0) { messageService.sendGroupMessage(any(), any(), any(), any()) }
  }
}
