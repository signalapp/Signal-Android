/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallException
import org.signal.ringrtc.GroupCall
import org.signal.ringrtc.PeekInfo
import org.thoughtcrime.securesms.components.webrtc.CallLinkProfileKeySender
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Process actions for when the call link has at least once been connected and joined.
 */
class CallLinkConnectedActionProcessor(
  actionProcessorFactory: MultiPeerActionProcessorFactory,
  webRtcInteractor: WebRtcInteractor
) : GroupConnectedActionProcessor(actionProcessorFactory, webRtcInteractor, TAG) {

  companion object {
    private val TAG = Log.tag(CallLinkConnectedActionProcessor::class.java)
  }

  override fun handleGroupRequestUpdateMembers(currentState: WebRtcServiceState): WebRtcServiceState {
    Log.i(tag, "handleGroupRequestUpdateMembers():")

    return currentState
  }

  override fun handleGroupJoinedMembershipChanged(currentState: WebRtcServiceState): WebRtcServiceState {
    Log.i(tag, "handleGroupJoinedMembershipChanged():")

    val superState: WebRtcServiceState = super.handleGroupJoinedMembershipChanged(currentState)
    val groupCall: GroupCall = superState.callInfoState.requireGroupCall()
    val peekInfo: PeekInfo = groupCall.peekInfo ?: return superState

    val callLinkRoomId: CallLinkRoomId = superState.callInfoState.callRecipient.requireCallLinkRoomId()
    val callLink: CallLinkTable.CallLink = SignalDatabase.callLinks.getCallLinkByRoomId(callLinkRoomId) ?: return superState
    val joinedParticipants: Set<Recipient> = peekInfo.joinedMembers.map { Recipient.externalPush(ServiceId.ACI.from(it)) }.toSet()

    CallLinkProfileKeySender.onRecipientsUpdated(joinedParticipants)

    if (callLink.credentials?.adminPassBytes == null) {
      Log.i(tag, "User is not an admin.")
      return superState
    }

    Log.i(tag, "Updating pending list with ${peekInfo.pendingUsers.size} entries.")
    val pendingParticipants: List<Recipient> = peekInfo.pendingUsers.map { Recipient.externalPush(ServiceId.ACI.from(it)) }

    Log.i(tag, "Storing peek-info in in-memory cache.")
    AppDependencies.signalCallManager.emitCallLinkPeekInfoUpdate(callLink.recipientId, peekInfo)

    return superState.builder()
      .changeCallInfoState()
      .setCallLinkPendingParticipants(pendingParticipants)
      .build()
  }

  override fun handleGroupCallEnded(currentState: WebRtcServiceState, groupCallHash: Int, groupCallEndReason: GroupCall.GroupCallEndReason): WebRtcServiceState {
    val serviceState = super.handleGroupCallEnded(currentState, groupCallHash, groupCallEndReason)

    val callLinkDisconnectReason = when (groupCallEndReason) {
      GroupCall.GroupCallEndReason.DENIED_REQUEST_TO_JOIN_CALL -> CallLinkDisconnectReason.DeniedRequestToJoinCall()
      GroupCall.GroupCallEndReason.REMOVED_FROM_CALL -> CallLinkDisconnectReason.RemovedFromCall()
      else -> null
    }

    return serviceState.builder()
      .changeCallInfoState()
      .setCallLinkDisconnectReason(callLinkDisconnectReason)
      .build()
  }

  override fun handleSetCallLinkJoinRequestAccepted(currentState: WebRtcServiceState, participant: RecipientId): WebRtcServiceState {
    Log.i(tag, "handleSetCallLinkJoinRequestAccepted():")

    val groupCall: GroupCall = currentState.callInfoState.requireGroupCall()
    val recipient = Recipient.resolved(participant)

    return try {
      groupCall.approveUser(recipient.requireAci().rawUuid)

      currentState
        .builder()
        .changeCallInfoState()
        .setCallLinkPendingParticipantApproved(recipient)
        .build()
    } catch (e: CallException) {
      Log.w(tag, "Failed to approve user.", e)

      currentState
    }
  }

  override fun handleSetCallLinkJoinRequestRejected(currentState: WebRtcServiceState, participant: RecipientId): WebRtcServiceState {
    Log.i(tag, "handleSetCallLinkJoinRequestRejected():")

    val groupCall: GroupCall = currentState.callInfoState.requireGroupCall()
    val recipient = Recipient.resolved(participant)

    return try {
      groupCall.denyUser(recipient.requireAci().rawUuid)

      currentState
        .builder()
        .changeCallInfoState()
        .setCallLinkPendingParticipantRejected(recipient)
        .build()
    } catch (e: CallException) {
      Log.w(tag, "Failed to deny user.", e)
      currentState
    }
  }

  override fun handleRemoveFromCallLink(currentState: WebRtcServiceState, participant: CallParticipant): WebRtcServiceState {
    Log.i(tag, "handleRemoveFromCallLink():")

    val groupCall: GroupCall = currentState.callInfoState.requireGroupCall()

    try {
      groupCall.removeClient(participant.callParticipantId.demuxId)
    } catch (e: CallException) {
      Log.w(tag, "Failed to remove user.", e)
    }

    return currentState
  }

  override fun handleBlockFromCallLink(currentState: WebRtcServiceState, participant: CallParticipant): WebRtcServiceState {
    Log.i(tag, "handleBlockFromCallLink():")

    val groupCall: GroupCall = currentState.callInfoState.requireGroupCall()

    try {
      groupCall.blockClient(participant.callParticipantId.demuxId)
    } catch (e: CallException) {
      Log.w(tag, "Failed to block user.", e)
    }

    return currentState
  }
}
