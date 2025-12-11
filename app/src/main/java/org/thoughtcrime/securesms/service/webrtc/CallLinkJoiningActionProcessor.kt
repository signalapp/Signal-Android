/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState

/**
 * Process actions to go from lobby to a joined call link.
 */
class CallLinkJoiningActionProcessor(
  actionProcessorFactory: MultiPeerActionProcessorFactory,
  webRtcInteractor: WebRtcInteractor
) : GroupJoiningActionProcessor(actionProcessorFactory, webRtcInteractor, TAG) {

  companion object {
    private val TAG = Log.tag(CallLinkJoiningActionProcessor::class.java)
  }

  override fun handleGroupRequestUpdateMembers(currentState: WebRtcServiceState): WebRtcServiceState {
    Log.i(tag, "handleGroupRequestUpdateMembers():")

    return currentState
  }

  override fun handleGroupCallEnded(currentState: WebRtcServiceState, groupCallHash: Int, groupCallEndReason: CallManager.CallEndReason): WebRtcServiceState {
    val serviceState = super.handleGroupCallEnded(currentState, groupCallHash, groupCallEndReason)

    val callLinkDisconnectReason = when (groupCallEndReason) {
      CallManager.CallEndReason.DENIED_REQUEST_TO_JOIN_CALL -> CallLinkDisconnectReason.DeniedRequestToJoinCall()
      CallManager.CallEndReason.REMOVED_FROM_CALL -> CallLinkDisconnectReason.RemovedFromCall()
      else -> null
    }

    return serviceState.builder()
      .changeCallInfoState()
      .setCallLinkDisconnectReason(callLinkDisconnectReason)
      .build()
  }
}
