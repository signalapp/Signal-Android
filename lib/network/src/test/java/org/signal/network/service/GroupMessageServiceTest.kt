/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsement
import org.signal.network.crypto.SealedSenderAccess
import org.signal.network.crypto.UnidentifiedAccess
import org.signal.network.service.GroupMessageService.SendTarget
import org.signal.network.service.GroupMessageService.SenderKeyAuthorization
import org.signal.network.service.GroupMessageService.SenderKeySendResult
import org.signal.network.service.MessageService.RecipientSendResult
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.StoryMessage
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class GroupMessageServiceTest {

  private val individualMessageService: IndividualMessageService = mockk()
  private val dependencies = FakeDependencies()
  private val now = 1_800_000_000_000L
  private val service = spyk(GroupMessageService(individualMessageService, mockk(), mockk(), mockk(), mockk(), dependencies, now = { now }))

  private val groupId = GroupIdentifier(ByteArray(32))
  private val distributionId = DistributionId.from(UUID.randomUUID())
  private val timestamp = 1_700_000_000_000L
  private val content = Content(dataMessage = DataMessage(timestamp = timestamp, body = "hi"))
  private val storyContent = Content(storyMessage = StoryMessage(allowsReplies = true))

  private val alice = recipient(1)
  private val bob = recipient(2)
  private val carol = recipient(3)

  private val sendTargets = slot<List<SendTarget>>()
  private val senderKeyTargets = slot<List<SendTarget>>()
  private val senderKeyAuth = slot<SenderKeyAuthorization>()

  @Before
  fun setUp() {
    coEvery { service.sendFanOut(capture(sendTargets), any(), any(), any(), any(), any()) } answers {
      firstArg<List<SendTarget>>().map { success(it.serviceId) }.onEach { arg<(RecipientSendResult) -> Unit>(5)(it) }
    }
    coEvery { service.sendMessageViaSenderKey(any(), capture(senderKeyTargets), any(), any(), any(), any(), capture(senderKeyAuth), any(), any(), any()) } answers {
      val results = secondArg<List<SendTarget>>().map { success(it.serviceId) }
      arg<(List<RecipientSendResult>) -> Unit>(9)(results)
      SenderKeySendResult(results, fanOut = emptyList()).right()
    }
  }

  @Test
  fun `group with endorsements uses sender key with a group send token`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, carol)

    val outcome = groupSend(alice, bob, carol)

    assertThat(senderKeyTargets.captured.map { it.serviceId }).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId, carol.serviceId)
    assertThat(senderKeyAuth.captured).isInstanceOf(SenderKeyAuthorization.GroupSend::class)
    assertThat(outcome.results().size).isEqualTo(3)
    coVerify(exactly = 0) { service.sendFanOut(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `targets sender key hands back are fanned out`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, carol)
    coEvery { service.sendMessageViaSenderKey(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      val targets = secondArg<List<SendTarget>>()
      SenderKeySendResult(targets.filter { it.serviceId != carol.serviceId }.map { success(it.serviceId) }, fanOut = targets.filter { it.serviceId == carol.serviceId }).right()
    }

    val outcome = groupSend(alice, bob, carol)

    assertThat(sendTargets.captured.map { it.serviceId }).containsExactly(carol.serviceId)
    assertThat(outcome.results().map { it.serviceId }).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId, carol.serviceId)
  }

  @Test
  fun `fewer than two sender key capable targets fans out to everyone`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, endorsed = setOf(alice.id))

    groupSend(alice, bob)

    assertThat(sendTargets.captured.map { it.serviceId }).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId)
    coVerify(exactly = 0) { service.sendMessageViaSenderKey(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `member without an endorsement is fanned out and reported`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, carol, endorsed = setOf(alice.id, bob.id))
    dependencies.refreshed = null

    groupSend(alice, bob, carol)

    assertThat(senderKeyTargets.captured.map { it.serviceId }).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId)
    assertThat(sendTargets.captured.map { it.serviceId }).containsExactly(carol.serviceId)
    assertThat(dependencies.missingEndorsementReports).isEqualTo(1)
  }

  @Test
  fun `stale endorsements are refreshed before sending`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, expirationMs = now + 1_000)
    dependencies.refreshed = sendInfo(alice, bob)

    groupSend(alice, bob)

    assertThat(dependencies.refreshes).isEqualTo(1)
    assertThat(senderKeyAuth.captured).isInstanceOf(SenderKeyAuthorization.GroupSend::class)
  }

  @Test
  fun `failed refresh with no endorsements at all fans out and reports the fallback`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, expirationMs = 0)
    dependencies.refreshed = null

    groupSend(alice, bob)

    assertThat(sendTargets.captured.map { it.serviceId }).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId)
    assertThat(dependencies.fallbackReports).isEqualTo(1)
  }

  @Test
  fun `not a member skips the refresh and fans out`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, expirationMs = 0, isMember = false)

    groupSend(alice, bob)

    assertThat(dependencies.refreshes).isEqualTo(0)
    assertThat(sendTargets.captured.map { it.serviceId }).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId)
  }

  @Test
  fun `distribution list story uses story access for sender key distribution and any fan-out`() = runTest {
    coEvery { service.sendMessageViaSenderKey(any(), capture(senderKeyTargets), any(), any(), any(), any(), capture(senderKeyAuth), any(), any(), any()) } answers {
      val targets = secondArg<List<SendTarget>>()
      SenderKeySendResult(listOf(success(alice.serviceId)), fanOut = targets.filter { it.serviceId == bob.serviceId }).right()
    }

    service.sendMessage(GroupSendDestination.DistributionListStory(distributionId), listOf(alice.recipient, bob.recipient), storyContent, ContentHint.IMPLICIT, timestamp, urgent = false)

    assertThat(senderKeyAuth.captured).isInstanceOf(SenderKeyAuthorization.Story::class)
    assertThat(senderKeyTargets.captured.all { it.sealedSenderAccess is SealedSenderAccess.Story }).isEqualTo(true)
    assertThat(sendTargets.captured.single().sealedSenderAccess).isNotNull().isInstanceOf(SealedSenderAccess.Story::class)
  }

  @Test
  fun `story sends leave non-ACI recipients out`() = runTest {
    val pniOnly = recipient(4, serviceId = ServiceId.PNI.from(UUID.randomUUID()))

    val outcome = service.sendMessage(GroupSendDestination.DistributionListStory(distributionId), listOf(alice.recipient, pniOnly.recipient), storyContent, ContentHint.IMPLICIT, timestamp, urgent = false)

    assertThat(senderKeyTargets.captured.map { it.serviceId }).containsExactly(alice.serviceId)
    assertThat(outcome.results().map { it.serviceId }).containsExactly(alice.serviceId)
    coVerify(exactly = 0) { service.sendFanOut(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `group story distributes our sender key with group access but fans out as a story`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob)
    coEvery { service.sendMessageViaSenderKey(any(), capture(senderKeyTargets), any(), any(), any(), any(), capture(senderKeyAuth), any(), any(), any()) } answers {
      val targets = secondArg<List<SendTarget>>()
      SenderKeySendResult(listOf(success(alice.serviceId)), fanOut = targets.filter { it.serviceId == bob.serviceId }).right()
    }

    service.sendMessage(GroupSendDestination.GroupStory(groupId), listOf(alice.recipient, bob.recipient), storyContent, ContentHint.IMPLICIT, timestamp, urgent = false)

    assertThat(senderKeyAuth.captured).isInstanceOf(SenderKeyAuthorization.Story::class)
    assertThat(senderKeyTargets.captured.none { it.sealedSenderAccess is SealedSenderAccess.Story }).isEqualTo(true)
    assertThat(sendTargets.captured.single().sealedSenderAccess).isNotNull().isInstanceOf(SealedSenderAccess.Story::class)
  }

  @Test
  fun `ourselves are left out of the send`() = runTest {
    val self = recipient(9, serviceId = dependencies.localAci)
    dependencies.sendInfo = sendInfo(alice, bob, self)

    groupSend(alice, bob, self)

    assertThat(senderKeyTargets.captured.map { it.serviceId }).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId)
  }

  @Test
  fun `no sender certificate fails the whole send`() = runTest {
    dependencies.certificate = null

    val outcome = groupSend(alice, bob)

    assertThat((outcome as Either.Left).value).isEqualTo(GroupSendError.NoSenderCertificate)
  }

  @Test
  fun `sender key failure for the whole send is returned`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob)
    coEvery { service.sendMessageViaSenderKey(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns MessageService.SendError.SessionAttemptsExhausted().left()

    val outcome = groupSend(alice, bob)

    assertThat(((outcome as Either.Left).value as GroupSendError.SendFailed).error).isInstanceOf(MessageService.SendError.SessionAttemptsExhausted::class)
  }

  @Test
  fun `every sent result is reported to onResult`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob, carol)
    coEvery { service.sendMessageViaSenderKey(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      val targets = secondArg<List<SendTarget>>()
      val results = targets.filter { it.serviceId != carol.serviceId }.map { success(it.serviceId) }
      arg<(List<RecipientSendResult>) -> Unit>(9)(results)
      SenderKeySendResult(results, fanOut = targets.filter { it.serviceId == carol.serviceId }).right()
    }
    val reported = mutableListOf<ServiceId>()

    service.sendMessage(GroupSendDestination.Group(groupId), listOf(alice.recipient, bob.recipient, carol.recipient), content, ContentHint.RESENDABLE, timestamp, urgent = true, onResult = { reported += it.serviceId })

    assertThat(reported).containsExactlyInAnyOrder(alice.serviceId, bob.serviceId, carol.serviceId)
  }

  @Test
  fun `online sends are passed through`() = runTest {
    dependencies.sendInfo = sendInfo(alice)

    service.sendMessage(GroupSendDestination.Group(groupId), listOf(alice.recipient), content, ContentHint.IMPLICIT, timestamp, isOnline = true, urgent = false)

    coVerify { service.sendFanOut(any(), any(), any(), isOnline = true, urgent = false, onResult = any()) }
  }

  @Test
  fun `old sender key is rotated before a sender key send`() = runTest {
    dependencies.sendInfo = sendInfo(alice, bob)
    dependencies.senderKeyCreatedAt = now - dependencies.senderKeyMaxAge.inWholeMilliseconds - 1

    groupSend(alice, bob)

    assertThat(dependencies.rotations).isEqualTo(1)
  }

  private suspend fun groupSend(vararg recipients: TestRecipient): Either<GroupSendError, List<RecipientSendResult>> {
    return service.sendMessage(GroupSendDestination.Group(groupId), recipients.map { it.recipient }, content, ContentHint.RESENDABLE, timestamp, urgent = true)
  }

  private fun Either<GroupSendError, List<RecipientSendResult>>.results(): List<RecipientSendResult> = (this as Either.Right).value

  private fun success(serviceId: ServiceId) = RecipientSendResult(serviceId, MessageService.SendSuccess(mockk(), true, listOf(1)).right())

  private fun recipient(id: Long, serviceId: ServiceId = ServiceId.ACI.from(UUID.randomUUID())): TestRecipient {
    return TestRecipient(GroupSendRecipient(id, serviceId, unidentifiedAccess = mockk<UnidentifiedAccess>(relaxed = true)))
  }

  private fun sendInfo(
    vararg members: TestRecipient,
    endorsed: Set<Long> = members.map { it.id }.toSet(),
    expirationMs: Long = now + 1.days.inWholeMilliseconds,
    isMember: Boolean = true
  ): GroupSendInfo {
    return GroupSendInfo(
      isMember = isMember,
      members = members.map { it.id }.toSet(),
      endorsements = members.associate { m -> m.id to if (m.id in endorsed) endorsement() else null },
      expirationMs = expirationMs,
      groupSecretParams = mockk()
    )
  }

  private fun endorsement(): GroupSendEndorsement = mockk { every { toFullToken(any(), any()) } returns mockk() }

  private data class TestRecipient(val recipient: GroupSendRecipient) {
    val id: Long get() = recipient.id
    val serviceId: ServiceId get() = recipient.serviceId
  }

  private inner class FakeDependencies : MessageSendDependencies {
    override val localAci: ServiceId.ACI = ServiceId.ACI.from(UUID.randomUUID())
    override val localDeviceId: Int = 1
    override val maxContentSizeBytes: Long = 0L
    override val senderKeyMaxAge: Duration = 14.days
    override val isSenderKeyMinimumRemoved: Boolean = false

    var certificate: SenderCertificate? = mockk()
    var sendInfo: GroupSendInfo? = null
    var refreshed: GroupSendInfo? = null
    var senderKeyCreatedAt: Long = -1

    var refreshes = 0
    var rotations = 0
    var missingEndorsementReports = 0
    var fallbackReports = 0

    override fun getSenderCertificate(): SenderCertificate? = certificate
    override fun getDistributionId(groupId: GroupIdentifier): DistributionId = distributionId
    override fun getGroupSendInfo(groupId: GroupIdentifier): GroupSendInfo? = sendInfo
    override fun refreshGroupSendEndorsements(groupId: GroupIdentifier): GroupSendInfo? = refreshed.also { refreshes++ }
    override fun getSenderKeyCreatedAt(distributionId: DistributionId): Long = senderKeyCreatedAt
    override fun rotateSenderKey(distributionId: DistributionId) {
      rotations++
    }

    override fun onMissingGroupSendEndorsement() {
      missingEndorsementReports++
    }

    override fun onGroupSendFallback() {
      fallbackReports++
    }

    override fun logSendResult(entryId: Long, recipientId: Long, serviceId: ServiceId, success: MessageService.SendSuccess, timestamp: Long, contentHint: ContentHint, messageId: Long, urgent: Boolean): Long = entryId
  }
}
