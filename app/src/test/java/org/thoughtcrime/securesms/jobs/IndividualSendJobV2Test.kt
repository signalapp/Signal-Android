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
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.signal.libsignal.net.ChallengeOption
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.network.service.MessageService
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageSendLogTables
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.PendingPniSignatureMessageTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.RecipientTable.SealedSenderAccessMode
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.keyvalue.MiscellaneousValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.ratelimit.ProofRequiredExceptionHandler
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.service.ExpiringMessageManager
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.DataMessageError
import org.thoughtcrime.securesms.util.MessageUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.toDataMessage
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.PniSignatureMessage
import java.io.IOException
import java.util.Optional
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for [IndividualSendJobV2]. Drives the job's [doRun] via [Job.run] (which delegates to the
 * coroutine entry point) with all of its collaborators mocked: [SignalStore], [SignalDatabase] tables,
 * [AppDependencies] (including [MessageService]), [Recipient] statics, and the various static helpers
 * the job depends on ([SealedSenderAccessUtil], [RecipientUtil], [PushSendJob], etc.).
 *
 * Tests verify both the [Job.Result] returned and the side effects on the database / job manager,
 * including for every [MessageService.SendError] variant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class IndividualSendJobV2Test {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val messageId: Long = 42L
  private val threadId: Long = 17L
  private val sentTime: Long = 1_700_000_000_000L
  private val selfAci: ACI = ACI.from(UUID.fromString("00000000-0000-4000-8000-000000000001"))
  private val recipientAci: ACI = ACI.from(UUID.fromString("00000000-0000-4000-8000-000000000002"))
  private val selfRecipientId: RecipientId = RecipientId.from(1L)
  private val recipientId: RecipientId = RecipientId.from(2L)

  private lateinit var misc: MiscellaneousValues

  private lateinit var recipient: Recipient
  private lateinit var self: Recipient
  private lateinit var outgoingMessage: OutgoingMessage
  private lateinit var messageRecord: MessageRecord
  private lateinit var dataMessage: DataMessage

  private lateinit var messages: MessageTable
  private lateinit var messageLog: MessageSendLogTables
  private lateinit var threads: ThreadTable
  private lateinit var recipientsTable: RecipientTable
  private lateinit var attachments: AttachmentTable
  private lateinit var pendingPniSignatureMessages: PendingPniSignatureMessageTable

  private lateinit var expiringMessageManager: ExpiringMessageManager
  private lateinit var messageService: MessageService

  @Before
  fun setUp() {
    misc = mockk(relaxUnitFun = true)
    every { misc.isClientDeprecated } returns false
    every { SignalStore.misc } returns misc

    every { signalStore.account.aci } returns selfAci
    every { signalStore.account.requireAci() } returns selfAci
    every { signalStore.account.isMultiDevice } returns false
    every { signalStore.account.aciPreKeys.lastSignedPreKeyRotationTime } returns System.currentTimeMillis()
    every { signalStore.account.pniPreKeys.lastSignedPreKeyRotationTime } returns System.currentTimeMillis()

    messages = mockk(relaxed = true)
    messageLog = mockk(relaxed = true)
    threads = mockk(relaxed = true)
    recipientsTable = mockk(relaxed = true)
    attachments = mockk(relaxed = true)
    pendingPniSignatureMessages = mockk(relaxed = true)

    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.messages } returns messages
    every { SignalDatabase.messageLog } returns messageLog
    every { SignalDatabase.threads } returns threads
    every { SignalDatabase.recipients } returns recipientsTable
    every { SignalDatabase.attachments } returns attachments
    every { SignalDatabase.pendingPniSignatureMessages } returns pendingPniSignatureMessages

    every { messageLog.insertIfPossible(any<RecipientId>(), any<Long>(), any(), any(), any<MessageId>(), any<Boolean>()) } returns 1L

    recipient = mockRecipient(recipientAci, isSelf = false)
    self = mockRecipient(selfAci, isSelf = true)
    every { self.isRegistered } returns true

    outgoingMessage = mockk {
      every { threadRecipient } returns recipient
      every { sentTimeMillis } returns sentTime
      every { body } returns "hello"
      every { messageToEdit } returns 0L
      every { expiresIn } returns 0L
      every { isExpirationUpdate } returns false
      every { isViewOnce } returns false
      every { attachments } returns emptyList<Attachment>()
    }

    messageRecord = mockk(relaxed = true)
    every { messageRecord.threadId } returns threadId
    every { messageRecord.dateSent } returns sentTime
    every { messageRecord.type } returns 0L

    every { messages.getOutgoingMessageOrNull(messageId) } returns outgoingMessage
    every { messages.getMessageRecordOrNull(messageId) } returns messageRecord

    every { threads.getRecipientForThreadId(threadId) } returns recipient

    dataMessage = DataMessage(timestamp = sentTime)
    mockkStatic("org.thoughtcrime.securesms.util.SignalServiceTransformExtensionsKt")
    every { outgoingMessage.toDataMessage() } returns dataMessage.right()

    mockkObject(Recipient.Companion)
    every { Recipient.self() } returns self

    mockkStatic(RecipientUtil::class)
    every { RecipientUtil.shareProfileIfFirstSecureMessage(any()) } just runs

    mockkStatic(SealedSenderAccessUtil::class)
    every { SealedSenderAccessUtil.getSealedSenderAccessFor(any<Recipient>()) } returns mockk(relaxed = true)
    every { SealedSenderAccessUtil.getSealedSenderCertificate() } returns mockk(relaxed = true)

    mockkObject(RemoteConfig)
    every { RemoteConfig.defaultMaxBackoff } returns 60_000L

    mockkObject(PushSendJob.Companion)
    every { PushSendJob.markAttachmentsUploaded(any<Long>(), any<OutgoingMessage>()) } just runs
    every { PushSendJob.notifyMediaMessageDeliveryFailed(any(), any<Long>()) } just runs

    mockkObject(ConversationShortcutRankingUpdateJob.Companion)
    every { ConversationShortcutRankingUpdateJob.enqueueForOutgoingIfNecessary(any()) } just runs

    mockkStatic(ProofRequiredExceptionHandler::class)

    messageService = AppDependencies.messageService
    expiringMessageManager = AppDependencies.expiringMessageManager
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  // region — Pre-send guards

  @Test
  fun `Given client is deprecated, when run, then return failure and skip send`() {
    every { SignalStore.misc.isClientDeprecated } returns true

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { messageService.sendMessage(serviceId = any(), envelopeContent = any(), timestamp = any(), sealedSenderAccess = any(), story = any(), isOnline = any(), urgent = any(), onEncrypted = any()) }
  }

  @Test
  fun `Given signed prekey age is too old, when run, then trigger PreKeysSyncJob and continue on success`() {
    val staleTime = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
    every { signalStore.account.aciPreKeys.lastSignedPreKeyRotationTime } returns staleTime
    stubSuccessfulSend()

    every {
      AppDependencies.jobManager.runSynchronously(any(), any())
    } returns Optional.of(JobTracker.JobState.SUCCESS)

    val result = createAndRunJob()

    assertThat(result.isSuccess).isTrue()
    verify { AppDependencies.jobManager.runSynchronously(match { it is PreKeysSyncJob }, any()) }
  }

  @Test
  fun `Given signed prekey age is too old and refresh fails, when run, then retry`() {
    val staleTime = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
    every { signalStore.account.pniPreKeys.lastSignedPreKeyRotationTime } returns staleTime

    every {
      AppDependencies.jobManager.runSynchronously(any(), any())
    } returns Optional.empty()

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
    coVerify(exactly = 0) { messageService.sendMessage(serviceId = any(), envelopeContent = any(), timestamp = any(), sealedSenderAccess = any(), story = any(), isOnline = any(), urgent = any(), onEncrypted = any()) }
  }

  @Test
  fun `Given self is not registered, when run, then return failure`() {
    every { self.isRegistered } returns false

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given outgoing message is missing, when run, then return failure`() {
    every { messages.getOutgoingMessageOrNull(messageId) } returns null

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given message record is missing, when run, then return failure`() {
    every { messages.getMessageRecordOrNull(messageId) } returns null

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given message is already sent, when run, then return success without sending`() {
    every { messageRecord.type } returns MessageTypes.BASE_SENT_TYPE

    val result = createAndRunJob()

    assertThat(result.isSuccess).isTrue()
    coVerify(exactly = 0) { messageService.sendMessage(serviceId = any(), envelopeContent = any(), timestamp = any(), sealedSenderAccess = any(), story = any(), isOnline = any(), urgent = any(), onEncrypted = any()) }
  }

  @Test
  fun `Given body exceeds inline size limit, when run, then return failure`() {
    val tooLargeBody = "x".repeat(MessageUtil.MAX_INLINE_BODY_SIZE_BYTES + 1)
    every { outgoingMessage.body } returns tooLargeBody

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { messageService.sendMessage(serviceId = any(), envelopeContent = any(), timestamp = any(), sealedSenderAccess = any(), story = any(), isOnline = any(), urgent = any(), onEncrypted = any()) }
  }

  @Test
  fun `Given recipient is unregistered, when run, then return failure`() {
    every { recipient.isUnregistered } returns true

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given recipient has no service id, when run, then return failure`() {
    every { recipient.hasServiceId } returns false

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given toDataMessage fails, when run, then return failure`() {
    every { outgoingMessage.toDataMessage() } returns DataMessageError.MissingAttachmentRemoteFields.left()

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { messageService.sendMessage(serviceId = any(), envelopeContent = any(), timestamp = any(), sealedSenderAccess = any(), story = any(), isOnline = any(), urgent = any(), onEncrypted = any()) }
  }

  // endregion

  // region — Successful send

  @Test
  fun `Given a successful send, when run, then mark sent, write to log, update thread, return success`() {
    stubSuccessfulSend(sentSealedSender = true)

    val result = createAndRunJob()

    assertThat(result.isSuccess).isTrue()
    verify { messages.markAsSent(messageId, true) }
    verify { threads.updateSilently(threadId, false) }
    verify { messageLog.insertIfPossible(recipientId, sentTime, any(), ContentHint.RESENDABLE, MessageId(messageId), any()) }
    verify { ConversationShortcutRankingUpdateJob.enqueueForOutgoingIfNecessary(recipient) }
  }

  @Test
  fun `Given a successful send and an expiring message, when run, then start expiration timer`() {
    every { outgoingMessage.expiresIn } returns 60_000L
    every { outgoingMessage.isExpirationUpdate } returns false
    stubSuccessfulSend()

    createAndRunJob()

    verify { messages.markExpireStarted(messageId, any()) }
    verify { expiringMessageManager.scheduleDeletion(messageId, true, 60_000L) }
  }

  @Test
  fun `Given a successful send and a view-once message, when run, then delete attachment files`() {
    every { outgoingMessage.isViewOnce } returns true
    stubSuccessfulSend()

    createAndRunJob()

    verify { attachments.deleteAttachmentFilesForViewOnceMessage(messageId) }
  }

  @Test
  fun `Given a UD send to unknown sealed-sender-mode recipient with no profile key, when run, then mark UNRESTRICTED`() {
    every { recipient.sealedSenderAccessMode } returns SealedSenderAccessMode.UNKNOWN
    every { recipient.profileKey } returns null
    stubSuccessfulSend(sentSealedSender = true)

    createAndRunJob()

    verify { recipientsTable.setSealedSenderAccessMode(recipientId, SealedSenderAccessMode.UNRESTRICTED) }
  }

  @Test
  fun `Given a UD send to unknown sealed-sender-mode recipient with profile key, when run, then mark ENABLED`() {
    every { recipient.sealedSenderAccessMode } returns SealedSenderAccessMode.UNKNOWN
    every { recipient.profileKey } returns ByteArray(32)
    stubSuccessfulSend(sentSealedSender = true)

    createAndRunJob()

    verify { recipientsTable.setSealedSenderAccessMode(recipientId, SealedSenderAccessMode.ENABLED) }
  }

  @Test
  fun `Given a non-UD send to an enabled recipient, when run, then mark DISABLED`() {
    every { recipient.sealedSenderAccessMode } returns SealedSenderAccessMode.ENABLED
    stubSuccessfulSend(sentSealedSender = false)

    createAndRunJob()

    verify { recipientsTable.setSealedSenderAccessMode(recipientId, SealedSenderAccessMode.DISABLED) }
  }

  @Test
  fun `Given multi-device, when send succeeds, then also send a sync transcript`() {
    every { signalStore.account.isMultiDevice } returns true
    stubSuccessfulSend()

    createAndRunJob()

    coVerify(exactly = 1) {
      messageService.sendMessage(
        serviceId = any(),
        envelopeContent = any(),
        timestamp = any(),
        sealedSenderAccess = any(),
        story = any(),
        isOnline = any(),
        urgent = any(),
        onEncrypted = any()
      )
    }
    coVerify(exactly = 1) {
      messageService.sendSyncMessage(
        timestamp = any(),
        envelopeContent = any(),
        urgent = any(),
        onEncrypted = any()
      )
    }
  }

  @Test
  fun `Given an edit of an already-edited message, when run, then target the edited revision's dateSent`() {
    val editTargetMessageId = messageId + 1
    val editRevisionSentTime = sentTime + 10_000L
    // An edit revision inherits the original message's dateReceived, so getTimestamp() returns a value
    // that is not the revision's actual sent timestamp. The edit target must use dateSent.
    val inheritedTimestamp = sentTime - 2L

    every { outgoingMessage.messageToEdit } returns editTargetMessageId

    val editTargetRecord: MessageRecord = mockk(relaxed = true)
    every { editTargetRecord.dateSent } returns editRevisionSentTime
    every { editTargetRecord.timestamp } returns inheritedTimestamp
    every { editTargetRecord.expireStarted } returns 0L
    every { messages.getMessageRecordOrNull(editTargetMessageId) } returns editTargetRecord

    val contentSlot = slot<EnvelopeContent>()
    coEvery {
      messageService.sendMessage(
        serviceId = any(),
        envelopeContent = capture(contentSlot),
        timestamp = any(),
        sealedSenderAccess = any(),
        story = any(),
        isOnline = any(),
        urgent = any(),
        onEncrypted = any()
      )
    } returns MessageService.SendSuccess(
      envelopeContent = EnvelopeContent.encrypted(Content(dataMessage = dataMessage), ContentHint.RESENDABLE, Optional.empty()),
      sentSealedSender = false,
      devices = listOf(1)
    ).right()

    createAndRunJob()

    assertThat(contentSlot.captured.content.get().editMessage!!.targetSentTimestamp).isEqualTo(editRevisionSentTime)
  }

  @Test
  fun `Given a send needs PNI signature, when send succeeds, then write a pending PNI signature record`() {
    every { recipient.needsPniSignature } returns true
    every { AppDependencies.signalServiceMessageSender.createPniSignatureMessage() } returns PniSignatureMessage()
    stubSuccessfulSend()

    createAndRunJob()

    verify { pendingPniSignatureMessages.insertIfNecessary(recipientId, sentTime, any()) }
  }

  @Test
  fun `Given a self-send, when run, then wrap content in a SyncMessage and use ContentHint IMPLICIT`() {
    every { outgoingMessage.threadRecipient } returns self
    every { messages.getMessageRecordOrNull(messageId) } returns messageRecord
    stubSuccessfulSend(sentSealedSender = false)

    createAndRunJob()

    // Self-send signals success via the certificate fallback, not the network-reported flag.
    verify { messages.markAsSent(messageId, true) }
  }

  // endregion

  // region — SendError variants

  @Test
  fun `Given IdentityMismatch, when run, then record mismatched identity and mark failed, return success`() {
    val untrusted = mockk<UntrustedIdentityException>(relaxed = true)
    val external = mockRecipient(recipientAci, isSelf = false)
    every { Recipient.external(recipientAci.toString()) } returns external

    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.IdentityMismatch(
      serviceId = recipientAci,
      exception = untrusted
    ).left()

    val result = createAndRunJob()

    assertThat(result.isSuccess).isTrue()
    verify { messages.addMismatchedIdentity(messageId, recipientId, any()) }
    verify { messages.markAsSentFailed(messageId) }
  }

  @Test
  fun `Given NotRegistered, when run, then mark failed and enqueue DirectoryRefreshJob, return success`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.NotRegistered().left()

    val result = createAndRunJob()

    assertThat(result.isSuccess).isTrue()
    verify { messages.markAsSentFailed(messageId) }
    verify { AppDependencies.jobManager.add(match { it is DirectoryRefreshJob }) }
  }

  @Test
  fun `Given Unauthorized, when run, then return failure`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.Unauthorized().left()

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given ChallengeRequired and handler says retry now, when run, then retry with zero backoff`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.ChallengeRequired(
      token = "tok",
      options = setOf(ChallengeOption.CAPTCHA),
      retryAfter = 1.seconds
    ).left()

    every {
      ProofRequiredExceptionHandler.handle(any(), any(), any(), any(), any())
    } returns ProofRequiredExceptionHandler.Result.RETRY_NOW

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given ChallengeRequired and handler says retry later, when run, then retry with backoff`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.ChallengeRequired(
      token = "tok",
      options = emptySet(),
      retryAfter = null
    ).left()

    every {
      ProofRequiredExceptionHandler.handle(any(), any(), any(), any(), any())
    } returns ProofRequiredExceptionHandler.Result.RETRY_LATER

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given ServerRejected, when run, then return failure`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.ServerRejected().left()

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given ContentTooLarge, when run, then return failure`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.ContentTooLarge(size = 9_999L, maxAllowed = 256L).left()

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given SessionAttemptsExhausted, when run, then return retry`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.SessionAttemptsExhausted().left()

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given PreKeyUnavailable, when run, then return retry`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.PreKeyUnavailable("no signed prekey").left()

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given RateLimited, when run, then return retry`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.RateLimited(retryAfter = 600.seconds).left()

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given NetworkError, when run, then return retry`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.NetworkError(IOException("boom")).left()

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given ApplicationError with a RuntimeException, when run, then return fatal failure`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.ApplicationError(IllegalStateException("nope")).left()

    val result = createAndRunJob()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given ApplicationError with a non-RuntimeException, when run, then return retry`() {
    coEvery {
      messageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
    } returns MessageService.SendError.ApplicationError(Exception("eventually retryable")).left()

    val result = createAndRunJob()

    assertThat(result.isRetry).isTrue()
  }

  // endregion

  // region — Helpers

  private fun createAndRunJob(): Job.Result {
    val job = IndividualSendJobV2(messageId, recipient, hasMedia = false, isScheduledSend = false)
    job.setContext(ApplicationProvider.getApplicationContext())
    return job.run()
  }

  private fun stubSuccessfulSend(sentSealedSender: Boolean = false) {
    val envelopeContent = EnvelopeContent.encrypted(Content(dataMessage = dataMessage), ContentHint.RESENDABLE, Optional.empty())
    coEvery {
      messageService.sendMessage(
        serviceId = any(),
        envelopeContent = any(),
        timestamp = any(),
        sealedSenderAccess = any(),
        story = any(),
        isOnline = any(),
        urgent = any(),
        onEncrypted = any()
      )
    } returns MessageService.SendSuccess(envelopeContent = envelopeContent, sentSealedSender = sentSealedSender, devices = listOf(1)).right()

    coEvery {
      messageService.sendSyncMessage(
        timestamp = any(),
        envelopeContent = any(),
        urgent = any(),
        onEncrypted = any()
      )
    } returns MessageService.SendSuccess(envelopeContent = envelopeContent, sentSealedSender = sentSealedSender, devices = listOf(1)).right()
  }

  /**
   * Builds a [Recipient] mock with the minimum stubs the job touches. [fresh] returns the mock itself
   * so the job's `recipient.fresh()` calls don't hit the live cache.
   */
  private fun mockRecipient(aci: ACI, isSelf: Boolean): Recipient {
    val rid = if (isSelf) selfRecipientId else recipientId
    val mock = mockk<Recipient>(relaxed = true)
    every { mock.id } returns rid
    every { mock.hasServiceId } returns true
    every { mock.isUnregistered } returns false
    every { mock.isRegistered } returns true
    every { mock.isGroup } returns false
    every { mock.isSelf } returns isSelf
    every { mock.serviceId } returns Optional.of(aci)
    every { mock.requireServiceId() } returns aci
    every { mock.e164 } returns Optional.empty()
    every { mock.needsPniSignature } returns false
    every { mock.profileKey } returns null
    every { mock.sealedSenderAccessMode } returns SealedSenderAccessMode.DISABLED
    every { mock.fresh() } returns mock
    return mock
  }

  // endregion
}
