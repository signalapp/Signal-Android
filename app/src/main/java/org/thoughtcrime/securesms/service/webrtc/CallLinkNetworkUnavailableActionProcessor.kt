/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState

/**
 * Process actions when the network is unavailable during a call link.
 * Unlike groups, call links do not create a group call object while waiting for the network.
 */
class CallLinkNetworkUnavailableActionProcessor(
  actionProcessorFactory: MultiPeerActionProcessorFactory,
  webRtcInteractor: WebRtcInteractor
) : GroupNetworkUnavailableActionProcessor(actionProcessorFactory, webRtcInteractor, TAG) {

  companion object {
    private val TAG = Log.tag(CallLinkNetworkUnavailableActionProcessor::class.java)
  }

  override fun handlePreJoinCall(currentState: WebRtcServiceState, remotePeer: RemotePeer): WebRtcServiceState {
    Log.i(TAG, "handlePreJoinCall():")

    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val activeNetworkInfo = connectivityManager.activeNetworkInfo

    if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
      val processor = getActionProcessorFactory().createPreJoinActionProcessor(webRtcInteractor)
      return processor.handlePreJoinCall(currentState.builder().actionProcessor(processor).build(), remotePeer)
    }

    return currentState.builder()
      .changeCallInfoState()
      .callState(WebRtcViewModel.State.NETWORK_FAILURE)
      .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
      .build()
  }

  override fun handleCancelPreJoinCall(currentState: WebRtcServiceState): WebRtcServiceState {
    Log.i(TAG, "handleCancelPreJoinCall():")

    WebRtcVideoUtil.deinitializeVideo(currentState)
    EglBaseWrapper.releaseEglBase(RemotePeer.GROUP_CALL_ID.longValue())

    return WebRtcServiceState(IdleActionProcessor(webRtcInteractor))
  }
}
