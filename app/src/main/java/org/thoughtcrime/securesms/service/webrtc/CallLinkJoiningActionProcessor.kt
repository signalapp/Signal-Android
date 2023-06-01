/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState

/**
 * Process actions to go from lobby to a joined call link.
 */
class CallLinkJoiningActionProcessor(
  webRtcInteractor: WebRtcInteractor
) : GroupJoiningActionProcessor(webRtcInteractor) {
  override fun getGroupNetworkUnavailableActionProcessor(): GroupNetworkUnavailableActionProcessor {
    return CallLinkNetworkUnavailableActionProcessor(webRtcInteractor)
  }

  override fun handleGroupRequestUpdateMembers(currentState: WebRtcServiceState): WebRtcServiceState {
    Log.i(tag, "handleGroupRequestUpdateMembers():")

    return currentState
  }
}
