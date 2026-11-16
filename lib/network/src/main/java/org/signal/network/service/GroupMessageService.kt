/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.jetbrains.annotations.VisibleForTesting
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.net.MismatchedDeviceException
import org.signal.libsignal.net.MultiRecipientSendAuthorization
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidRegistrationIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.libsignal.zkgroup.groupsend.GroupSendFullToken
import org.signal.network.api.MessageApiV2
import org.signal.network.crypto.SealedSenderAccess
import org.signal.network.service.IndividualMessageService.Companion.EncryptionDispatcher
import org.signal.network.service.MessageService.RecipientSendResult
import org.signal.network.service.MessageService.SendError
import org.signal.network.service.MessageService.SendSuccess
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SignalGroupSessionBuilder
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.groupsv2.GroupSendEndorsements
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.Content
import java.time.Instant
import java.util.Optional
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** Sends to a group or story audience, with sender key where possible and a fan-out of individual sends otherwise. */
class GroupMessageService @JvmOverloads constructor(
  private val individualMessageService: IndividualMessageService,
  private val messageApi: MessageApiV2,
  private val protocolStore: SignalServiceAccountDataStore,
  private val sessionLock: SignalSessionLock,
  private val cipher: SignalServiceCipher,
  private val dependencies: MessageSendDependencies,
  private val now: () -> Long = System::currentTimeMillis
) {

  companion object {
    private val TAG = Log.tag(GroupMessageService::class)

    private const val MIN_SENDER_KEY_TARGETS = 2
    private const val MAX_SENDER_KEY_ATTEMPTS = 3
    private val ENDORSEMENT_REFRESH_WINDOW = 2.hours

    @VisibleForTesting
    internal const val MAX_CONCURRENT_SENDS = 32
  }

  /**
   * Sends [content] to [recipients], using sender key where we can and individual sends otherwise.
   *
   * 1. Leave ourselves out, since our linked devices get the caller's sync transcript.
   * 2. For a group, load its send endorsements, refreshing them first if they are about to expire or are missing for some members.
   *    A distribution list story has none.
   * 3. Pick each recipient's sealed sender access ([toTarget]) and split them between sender key and fan-out ([splitTargets]).
   * 4. Rotate our sender key if it is too old, then do the sender key send. Recipients it can't reach are moved to the fan-out.
   * 5. Send individually to everyone in the fan-out.
   *
   * The whole send only fails without a sender certificate or when the sender key send itself fails. Everything else is a per-recipient
   * result. [onResult] is called for each recipient actually sent to, possibly concurrently.
   */
  suspend fun sendMessage(
    destination: GroupSendDestination,
    recipients: List<GroupSendRecipient>,
    content: Content,
    contentHint: ContentHint,
    timestamp: Long,
    isOnline: Boolean = false,
    urgent: Boolean,
    onResult: (RecipientSendResult) -> Unit = {}
  ): Either<GroupSendError, List<RecipientSendResult>> = withContext(Dispatchers.IO) {
    either {
      val localAci = dependencies.localAci
      val others = recipients.filter { it.serviceId != localAci }

      val groupId = when (destination) {
        is GroupSendDestination.Group -> destination.groupId
        is GroupSendDestination.GroupStory -> destination.groupId
        is GroupSendDestination.DistributionListStory -> null
      }
      val distributionId = when (destination) {
        is GroupSendDestination.Group -> dependencies.getDistributionId(destination.groupId)
        is GroupSendDestination.GroupStory -> dependencies.getDistributionId(destination.groupId)
        is GroupSendDestination.DistributionListStory -> destination.distributionId
      }

      Log.i(TAG, "[$timestamp] Starting group send. Destination: ${destination::class.simpleName}, DistributionId: $distributionId, Targets: ${others.size}")

      val senderCertificate = dependencies.getSenderCertificate() ?: raise(GroupSendError.NoSenderCertificate)

      val sendInfo = groupId?.let { loadGroupSendInfo(it, timestamp) }
      val targets = others.map { it.toTarget(senderCertificate, sendInfo, destination) }
      val (senderKeySend, fanOutTargets) = splitTargets(targets, sendInfo, senderCertificate, destination.isStory, timestamp)

      val results = mutableListOf<RecipientSendResult>()
      val fanOut = fanOutTargets.toMutableList()

      if (senderKeySend != null) {
        rotateSenderKeyIfOld(distributionId, timestamp)

        val senderKeyResult = sendMessageViaSenderKey(
          distributionId = distributionId,
          targets = senderKeySend.targets.map { it.skdmTarget },
          content = content,
          contentHint = contentHint,
          groupId = groupId?.serialize(),
          timestamp = timestamp,
          authorization = senderKeySend.authorization,
          isOnline = isOnline,
          urgent = urgent,
          onResults = { batch -> batch.forEach(onResult) }
        ).mapLeft { GroupSendError.SendFailed(it) }.bind()

        results += senderKeyResult.results
        val fannedOut = senderKeyResult.fanOut.map { it.serviceId }.toSet()
        fanOut += senderKeySend.targets.filter { it.serviceId in fannedOut }
        Log.d(TAG, "[$timestamp] Sender key settled ${senderKeyResult.results.size}/${senderKeySend.targets.size}, ${fannedOut.size} fall back to fan-out")
      }

      if (fanOut.isNotEmpty()) {
        Log.i(TAG, "[$timestamp] Fanning out to ${fanOut.size} targets")
        results += sendFanOut(
          targets = fanOut.map { it.contentTarget },
          envelopeContent = EnvelopeContent.encrypted(content, contentHint, Optional.ofNullable(groupId?.serialize())),
          timestamp = timestamp,
          isOnline = isOnline,
          urgent = urgent,
          onResult = onResult
        )
      }

      results
    }
  }

  /**
   * Sends [envelopeContent] to each of [targets] with [IndividualMessageService.sendMessage], at most [MAX_CONCURRENT_SENDS] at a time, after fetching
   * prekeys for every target we have no session with. Results are in the same order as [targets], and [onResult] is called
   * with each one as soon as it is known, possibly concurrently.
   */
  @VisibleForTesting
  internal suspend fun sendFanOut(
    targets: List<SendTarget>,
    envelopeContent: EnvelopeContent,
    timestamp: Long,
    isOnline: Boolean,
    urgent: Boolean,
    onResult: (RecipientSendResult) -> Unit = {}
  ): List<RecipientSendResult> = coroutineScope {
    val permits = Semaphore(MAX_CONCURRENT_SENDS)

    prefetchPreKeys(targets, permits, timestamp)

    targets.map { target ->
      async {
        permits.withPermit {
          val result = individualMessageService.sendMessage(target.serviceId, envelopeContent, timestamp, target.sealedSenderAccess, isOnline, urgent)
          RecipientSendResult(target.serviceId, result).also(onResult)
        }
      }
    }.awaitAll()
  }

  /** Failures are ignored here because the target's own send runs into them again and reports them. */
  private suspend fun prefetchPreKeys(targets: List<SendTarget>, permits: Semaphore, timestamp: Long) = coroutineScope {
    val needsSession = targets.filter { !protocolStore.containsSession(SignalProtocolAddress(it.serviceId.libSignalServiceId, SignalServiceAddress.DEFAULT_DEVICE_ID)) }
    if (needsSession.isEmpty()) {
      return@coroutineScope
    }

    Log.i(TAG, "[$timestamp] Prefetching prekeys for ${needsSession.size} of ${targets.size} targets")
    needsSession.map { target ->
      async {
        permits.withPermit {
          val address = SignalProtocolAddress(target.serviceId.libSignalServiceId, SignalServiceAddress.DEFAULT_DEVICE_ID)
          individualMessageService.initializeSession(target.serviceId, address, target.sealedSenderAccess)
        }
      }
    }.awaitAll()
  }

  /**
   * Sends [content] to [targets] with one sender key multi-recipient send. Each target's [SendTarget.sealedSenderAccess] is used
   * to send it our sender key if it needs it. Targets that fail along the way are settled with their failure, and targets that
   * can't use sender key are returned in [SenderKeySendResult.fanOut] for the caller to send with [sendFanOut].
   */
  @VisibleForTesting
  internal suspend fun sendMessageViaSenderKey(
    distributionId: DistributionId,
    targets: List<SendTarget>,
    content: Content,
    contentHint: ContentHint,
    groupId: ByteArray?,
    timestamp: Long,
    authorization: SenderKeyAuthorization,
    isOnline: Boolean,
    urgent: Boolean,
    onResults: (List<RecipientSendResult>) -> Unit = {}
  ): Either<SendError, SenderKeySendResult> = withContext(Dispatchers.IO) {
    either {
      val envelopeContent = EnvelopeContent.encrypted(content, contentHint, Optional.ofNullable(groupId))
      individualMessageService.checkContentSize(envelopeContent, timestamp).bind()

      if (targets.any { it.serviceId !is ServiceId.ACI }) {
        raise(SendError.ApplicationError(IllegalArgumentException("Sender key targets must be ACIs")))
      }

      if (targets.any { it.serviceId == dependencies.localAci }) {
        raise(SendError.ApplicationError(IllegalArgumentException("Use sendSyncMessage to send to the local account")))
      }

      val state = SenderKeySendState(targets, onResults)
      var recoveryAttempts = 0

      while (state.hasRemaining()) {
        Log.d(TAG, "[$timestamp] Starting sender key send to ${state.remaining.size} targets with $distributionId")

        state.settle(distributeSenderKey(distributionId, state.remaining, groupId, urgent))
        if (!state.hasRemaining()) {
          break
        }

        if (authorization is SenderKeyAuthorization.GroupSend) {
          val withoutEndorsement = state.remaining.filter { authorization.endorsements.endorsements[it.serviceId] == null }
          if (withoutEndorsement.isNotEmpty()) {
            Log.w(TAG, "[$timestamp] ${withoutEndorsement.size} targets have no group send endorsement. Fanning out to them.")
            state.moveToFanOut(withoutEndorsement)
            continue
          }
        }

        val targetInfo = buildSenderKeyTargetInfo(state.remaining)
        val ciphertext = when (val encrypted = encryptForGroup(distributionId, targetInfo, content, contentHint, groupId, authorization)) {
          is GroupEncryptResult.Success -> encrypted.ciphertext
          is GroupEncryptResult.IdentityMismatch -> {
            Log.w(TAG, "[$timestamp] Identity mismatch for ${encrypted.serviceId} during sender key encryption. Leaving them out.")
            state.settle(listOf(RecipientSendResult(encrypted.serviceId, Either.Left(SendError.IdentityMismatch(encrypted.serviceId, encrypted.exception)))))
            continue
          }
          is GroupEncryptResult.TargetCannotUseSenderKey -> {
            Log.w(TAG, "[$timestamp] Can't use sender key for ${encrypted.serviceId} (${encrypted.reason}). Fanning out to them.")
            state.moveToFanOut(state.remaining.filter { it.serviceId == encrypted.serviceId })
            continue
          }
          is GroupEncryptResult.SenderKeyUnavailable -> {
            Log.w(TAG, "[$timestamp] Can't use sender key (${encrypted.reason}). Fanning out to everyone left.")
            state.moveToFanOut(state.remaining)
            continue
          }
        }

        val multiRecipientAuth = when (authorization) {
          is SenderKeyAuthorization.GroupSend -> MultiRecipientSendAuthorization.GroupSend(authorization.endorsements.toFullToken(state.remaining.map { it.serviceId }))
          is SenderKeyAuthorization.Story -> MultiRecipientSendAuthorization.Story
        }

        when (val result = messageApi.sendMultiRecipientMessage(ciphertext, timestamp, multiRecipientAuth, isOnline, urgent)) {
          is RequestResult.Success -> {
            val unregistered = result.result.unregisteredIds.map { ServiceId.fromLibSignal(it) }.toSet()
            Log.d(TAG, "[$timestamp] Sent with sender key to ${state.remaining.size - unregistered.size}/${state.remaining.size} targets")
            state.settle(
              state.remaining.map { target ->
                val outcome: Either<SendError, SendSuccess> = if (target.serviceId in unregistered) {
                  Either.Left(SendError.NotRegistered())
                } else {
                  Either.Right(SendSuccess(envelopeContent = envelopeContent, sentSealedSender = true, devices = targetInfo.devicesByServiceId[target.serviceId].orEmpty()))
                }
                RecipientSendResult(target.serviceId, outcome)
              }
            )
          }
          is RequestResult.NonSuccess -> {
            when (val error = result.error) {
              is MismatchedDeviceException -> {
                recoveryAttempts++
                if (recoveryAttempts >= MAX_SENDER_KEY_ATTEMPTS) {
                  Log.w(TAG, "[$timestamp] Exhausted device-recovery attempts for sender key send")
                  raise(SendError.SessionAttemptsExhausted())
                }
                Log.w(TAG, "[$timestamp] Handling mismatched devices for sender key send: ${error.entries.contentToString()}")
                state.settle(
                  error.entries.mapNotNull { entry ->
                    val serviceId = ServiceId.fromLibSignal(entry.account)
                    individualMessageService.handleMismatchedEntry(entry, sealedSenderAccess = null, timestamp).fold(
                      ifLeft = { RecipientSendResult(serviceId, Either.Left(it)) },
                      ifRight = { null }
                    )
                  }
                )
              }
              is RequestUnauthorizedException -> {
                Log.w(TAG, "[$timestamp] Multi-recipient send was unauthorized. Fanning out to everyone left.")
                state.moveToFanOut(state.remaining)
              }
            }
          }
          is RequestResult.RetryableNetworkError -> raise(result.toSendError())
          is RequestResult.ApplicationError -> raise(SendError.ApplicationError(result.cause))
        }
      }

      state.toResult()
    }
  }

  private suspend fun encryptForGroup(
    distributionId: DistributionId,
    targetInfo: SenderKeyTargetInfo,
    content: Content,
    contentHint: ContentHint,
    groupId: ByteArray?,
    authorization: SenderKeyAuthorization
  ): GroupEncryptResult {
    val senderCertificate = when (authorization) {
      is SenderKeyAuthorization.GroupSend -> authorization.endorsements.sealedSenderCertificate
      is SenderKeyAuthorization.Story -> authorization.senderCertificate
    }

    return try {
      val ciphertext = withContext(EncryptionDispatcher) {
        cipher.encryptForGroup(distributionId, targetInfo.destinations, targetInfo.sessions, senderCertificate, content.encode(), contentHint, Optional.ofNullable(groupId))
      }
      GroupEncryptResult.Success(ciphertext)
    } catch (e: UntrustedIdentityException) {
      GroupEncryptResult.IdentityMismatch(ServiceId.parseOrThrow(e.name), e)
    } catch (e: NoSessionException) {
      val address = e.address
      if (address != null) {
        GroupEncryptResult.TargetCannotUseSenderKey(ServiceId.parseOrThrow(address.name), "No session")
      } else {
        GroupEncryptResult.SenderKeyUnavailable("No session")
      }
    } catch (e: InvalidRegistrationIdException) {
      GroupEncryptResult.TargetCannotUseSenderKey(ServiceId.parseOrThrow(e.address.name), "Invalid registration id")
    } catch (e: InvalidKeyException) {
      GroupEncryptResult.SenderKeyUnavailable("Invalid key")
    }
  }

  private suspend fun distributeSenderKey(distributionId: DistributionId, targets: List<SendTarget>, groupId: ByteArray?, urgent: Boolean): List<RecipientSendResult> {
    val sharedWith = protocolStore.getSenderKeySharedWith(distributionId)
    val targetInfo = buildSenderKeyTargetInfo(targets)
    val needsKey = targets.filter { target ->
      val devices = targetInfo.devicesByServiceId[target.serviceId].orEmpty()
      devices.any { deviceId ->
        val address = SignalProtocolAddress(target.serviceId.toString(), deviceId)
        address !in sharedWith || targetInfo.sessions[address] == null
      }
    }

    if (needsKey.isEmpty()) {
      return emptyList()
    }

    val timestamp = System.currentTimeMillis()
    Log.i(TAG, "[$timestamp] Sending our sender key for $distributionId to ${needsKey.size} targets")

    val distributionMessage = createSenderKeyDistributionMessage(distributionId)
    val skdmContent = EnvelopeContent.encrypted(
      Content(senderKeyDistributionMessage = distributionMessage.serialize().toByteString()),
      ContentHint.IMPLICIT,
      Optional.ofNullable(groupId)
    )

    val results = sendFanOut(needsKey, skdmContent, timestamp, isOnline = false, urgent = urgent)

    val sharedAddresses = results.flatMap { result ->
      result.result.getOrNull()?.devices.orEmpty().map { SignalProtocolAddress(result.serviceId.toString(), it) }
    }
    protocolStore.markSenderKeySharedWith(distributionId, sharedAddresses)

    val failures = results.filter { it.result.isLeft() }
    if (failures.isNotEmpty()) {
      Log.w(TAG, "[$timestamp] Failed to send our sender key to ${failures.size}/${needsKey.size} targets. Leaving them out.")
    }
    return failures
  }

  private fun buildSenderKeyTargetInfo(targets: List<SendTarget>): SenderKeyTargetInfo {
    val sessions = protocolStore.getAllAddressesWithActiveSessions(targets.map { it.serviceId.toString() })
    val destinations = sessions.keys + targets.map { SignalProtocolAddress(it.serviceId.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID) }
    val devicesByName = destinations.groupBy({ it.name }, { it.deviceId })

    return SenderKeyTargetInfo(
      destinations = destinations.toList(),
      sessions = sessions,
      devicesByServiceId = targets.associate { it.serviceId to devicesByName[it.serviceId.toString()].orEmpty().sorted() }
    )
  }

  @VisibleForTesting
  internal fun createSenderKeyDistributionMessage(distributionId: DistributionId): SenderKeyDistributionMessage {
    return SignalGroupSessionBuilder(sessionLock, GroupSessionBuilder(protocolStore)).create(SignalProtocolAddress(dependencies.localAci.toString(), dependencies.localDeviceId), distributionId.asUuid())
  }

  /** Returns null when there are no usable endorsements and everyone must be fanned out to. */
  private fun loadGroupSendInfo(groupId: GroupIdentifier, timestamp: Long): GroupSendInfo? {
    val info = dependencies.getGroupSendInfo(groupId) ?: return null

    val needsRefresh = info.expirationMs == 0L ||
      (info.expirationMs - now()).milliseconds < ENDORSEMENT_REFRESH_WINDOW ||
      info.endorsements.values.any { it == null }

    if (!needsRefresh) {
      return info
    }

    if (!info.isMember) {
      Log.i(TAG, "[$timestamp] Not a member of the group, can't refresh group send endorsements. Fanning out.")
      return null
    }

    val refreshed = dependencies.refreshGroupSendEndorsements(groupId)
    return when {
      refreshed != null -> refreshed
      info.expirationMs == 0L -> {
        Log.w(TAG, "[$timestamp] Unable to refresh group send endorsements and have none. Fanning out.")
        dependencies.onGroupSendFallback()
        null
      }
      else -> {
        Log.w(TAG, "[$timestamp] Unable to refresh group send endorsements. Using what we have.")
        info
      }
    }
  }

  /**
   * Picks the sealed sender access for each way we might send to this recipient. [Target.skdmTarget] is used to give them our sender key,
   * and [Target.contentTarget] is used if the content itself is fanned out to them.
   */
  private fun GroupSendRecipient.toTarget(senderCertificate: SenderCertificate, sendInfo: GroupSendInfo?, destination: GroupSendDestination): Target {
    val token = groupSendToken(sendInfo)

    // Prefer their access key, keeping the token as a fallback if the server rejects it. With neither the send is unsealed.
    val individualAccess: SealedSenderAccess? = when {
      unidentifiedAccess != null -> SealedSenderAccess.IndividualUnidentifiedAccessFirst(unidentifiedAccess) { token }
      token != null -> SealedSenderAccess.IndividualGroupSendTokenFirst(token, senderCertificate)
      else -> null
    }

    // Stories are always sealed with story authorization. The individual access is only used to fetch prekeys.
    val storyAccess = if (destination.isStory) SealedSenderAccess.Story(senderCertificate, preKeyAccess = individualAccess) else null

    // The SKDM is not a story, so it goes as a normal 1:1 send. A distribution list has no endorsements to fall back on, so it uses story authorization.
    val skdmAccess = when (destination) {
      is GroupSendDestination.Group, is GroupSendDestination.GroupStory -> individualAccess
      is GroupSendDestination.DistributionListStory -> storyAccess
    }

    return Target(
      recipient = this,
      serviceId = serviceId,
      hasEndorsement = token != null,
      skdmTarget = SendTarget(serviceId, skdmAccess),
      contentTarget = SendTarget(serviceId, storyAccess ?: individualAccess)
    )
  }

  private fun GroupSendRecipient.groupSendToken(sendInfo: GroupSendInfo?): GroupSendFullToken? {
    if (sendInfo == null || serviceId !is ServiceId.ACI || id !in sendInfo.members) {
      return null
    }

    val endorsement = sendInfo.endorsements[id] ?: return null
    return endorsement.toFullToken(sendInfo.groupSecretParams, Instant.ofEpochMilli(sendInfo.expirationMs))
  }

  /**
   * Splits [targets] between one sender key send and individual fan-out sends:
   * - Stories: sender key for every ACI target. Anyone else is left out.
   * - Groups without usable endorsements: everyone fans out.
   * - Groups with endorsements: endorsed members use sender key and the rest fan out. If fewer than [MIN_SENDER_KEY_TARGETS] are endorsed,
   *   everyone fans out.
   */
  private fun splitTargets(
    targets: List<Target>,
    sendInfo: GroupSendInfo?,
    senderCertificate: SenderCertificate,
    story: Boolean,
    timestamp: Long
  ): Pair<SenderKeySend?, List<Target>> {
    if (story) {
      val (aciTargets, others) = targets.partition { it.serviceId is ServiceId.ACI }
      if (others.isNotEmpty()) {
        Log.w(TAG, "[$timestamp] Leaving ${others.size} non-ACI targets out of a story send.")
      }
      Log.i(TAG, "[$timestamp] Story send. Using sender key for all ${aciTargets.size} ACI targets.")
      return SenderKeySend.of(aciTargets, SenderKeyAuthorization.Story(senderCertificate)) to emptyList()
    }

    if (sendInfo == null) {
      Log.i(TAG, "[$timestamp] No group send endorsements. Fanning out to all ${targets.size} targets.")
      return null to targets
    }

    val (senderKey, fanOut) = targets.partition { it.hasEndorsement }
    if (fanOut.any { it.recipient.id in sendInfo.members }) {
      Log.w(TAG, "[$timestamp] Missing group send endorsements for some members")
      dependencies.onMissingGroupSendEndorsement()
    }

    if (senderKey.size < MIN_SENDER_KEY_TARGETS && !dependencies.isSenderKeyMinimumRemoved) {
      Log.i(TAG, "[$timestamp] Too few sender key targets (${senderKey.size}). Fanning out to everyone.")
      return null to targets
    }

    return SenderKeySend.of(senderKey, SenderKeyAuthorization.GroupSend(sendInfo.toEndorsements(senderCertificate, senderKey))) to fanOut
  }

  private fun rotateSenderKeyIfOld(distributionId: DistributionId, timestamp: Long) {
    val createdAt = dependencies.getSenderKeyCreatedAt(distributionId)
    if (createdAt != -1L && (now() - createdAt).milliseconds > dependencies.senderKeyMaxAge) {
      Log.w(TAG, "[$timestamp] Our sender key for $distributionId is older than ${dependencies.senderKeyMaxAge}. Rotating.")
      dependencies.rotateSenderKey(distributionId)
    }
  }

  private fun GroupSendInfo.toEndorsements(senderCertificate: SenderCertificate, targets: List<Target>): GroupSendEndorsements {
    return GroupSendEndorsements(
      expirationMs = expirationMs,
      endorsements = targets
        .mapNotNull { target -> endorsements[target.recipient.id]?.let { (target.serviceId as ServiceId.ACI) to it } }
        .toMap(),
      sealedSenderCertificate = senderCertificate,
      groupSecretParams = groupSecretParams
    )
  }

  data class SendTarget(val serviceId: ServiceId, val sealedSenderAccess: SealedSenderAccess?)

  sealed interface SenderKeyAuthorization {
    data class GroupSend(val endorsements: GroupSendEndorsements) : SenderKeyAuthorization
    data class Story(val senderCertificate: SenderCertificate) : SenderKeyAuthorization
  }

  data class SenderKeySendResult(val results: List<RecipientSendResult>, val fanOut: List<SendTarget>)

  private class SenderKeySendState(targets: List<SendTarget>, private val onResults: (List<RecipientSendResult>) -> Unit) {
    var remaining: List<SendTarget> = targets
      private set

    private val settled = mutableListOf<RecipientSendResult>()
    private val fanOut = mutableListOf<SendTarget>()

    fun hasRemaining(): Boolean = remaining.isNotEmpty()

    fun settle(results: List<RecipientSendResult>) {
      if (results.isEmpty()) {
        return
      }
      settled += results
      val settledIds = results.map { it.serviceId }.toSet()
      remaining = remaining.filterNot { it.serviceId in settledIds }
      onResults(results)
    }

    fun moveToFanOut(targets: List<SendTarget>) {
      fanOut += targets
      remaining = remaining - targets.toSet()
    }

    fun toResult(): SenderKeySendResult = SenderKeySendResult(settled.toList(), fanOut.toList())
  }

  private sealed interface GroupEncryptResult {
    class Success(val ciphertext: ByteArray) : GroupEncryptResult
    data class IdentityMismatch(val serviceId: ServiceId, val exception: UntrustedIdentityException) : GroupEncryptResult
    data class TargetCannotUseSenderKey(val serviceId: ServiceId, val reason: String) : GroupEncryptResult
    data class SenderKeyUnavailable(val reason: String) : GroupEncryptResult
  }

  private class SenderKeyTargetInfo(
    val destinations: List<SignalProtocolAddress>,
    val sessions: Map<SignalProtocolAddress, SessionRecord>,
    val devicesByServiceId: Map<ServiceId, List<Int>>
  )

  private class SenderKeySend(val targets: List<Target>, val authorization: SenderKeyAuthorization) {
    companion object {
      fun of(targets: List<Target>, authorization: SenderKeyAuthorization): SenderKeySend? = if (targets.isEmpty()) null else SenderKeySend(targets, authorization)
    }
  }

  private class Target(
    val recipient: GroupSendRecipient,
    val serviceId: ServiceId,
    val hasEndorsement: Boolean,
    val skdmTarget: SendTarget,
    val contentTarget: SendTarget
  )
}

/** Who a group send goes to. Group stories use story authorization but still use the group's endorsements for sender key distribution. */
sealed interface GroupSendDestination {
  data class Group(val groupId: GroupIdentifier) : GroupSendDestination
  data class GroupStory(val groupId: GroupIdentifier) : GroupSendDestination
  data class DistributionListStory(val distributionId: DistributionId) : GroupSendDestination

  val isStory: Boolean
    get() = when (this) {
      is Group -> false
      is GroupStory -> true
      is DistributionListStory -> true
    }
}

sealed interface GroupSendError {
  data object NoSenderCertificate : GroupSendError
  data class SendFailed(val error: MessageService.SendError) : GroupSendError
}
