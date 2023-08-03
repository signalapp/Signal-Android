/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallException
import org.signal.ringrtc.GroupCall
import org.signal.ringrtc.PeekInfo
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
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

    if (callLink.credentials?.adminPassBytes == null) {
      Log.i(tag, "User is not an admin.")
      return superState
    }

    Log.i(tag, "Updating pending list with ${peekInfo.pendingUsers.size} entries.")
    val pendingParticipants: List<Recipient> = peekInfo.pendingUsers.map { Recipient.externalPush(ServiceId.ACI.from(it)) }

    return superState.builder()
      .changeCallInfoState()
      .setPendingParticipants(pendingParticipants)
      .build()
  }

  override fun handleSetCallLinkJoinRequestAccepted(currentState: WebRtcServiceState, participant: Recipient): WebRtcServiceState {
    Log.i(tag, "handleSetCallLinkJoinRequestAccepted():")

    val groupCall: GroupCall = currentState.callInfoState.requireGroupCall()

    return try {
      groupCall.approveUser(participant.requireAci().rawUuid)

      currentState
        .builder()
        .changeCallInfoState()
        .setPendingParticipantApproved(participant)
        .build()
    } catch (e: CallException) {
      Log.w(tag, "Failed to approve user.", e)

      currentState
    }
  }

  override fun handleSetCallLinkJoinRequestRejected(currentState: WebRtcServiceState, participant: Recipient): WebRtcServiceState {
    Log.i(tag, "handleSetCallLinkJoinRequestRejected():")

    val groupCall: GroupCall = currentState.callInfoState.requireGroupCall()

    return try {
      groupCall.denyUser(participant.requireAci().rawUuid)

      currentState
        .builder()
        .changeCallInfoState()
        .setPendingParticipantRejected(participant)
        .build()
    } catch (e: CallException) {
      Log.w(tag, "Failed to deny user.", e)
      currentState
    }
  }
}
