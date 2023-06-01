/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

/**
 * Processor which is utilized when the network becomes unavailable during a call link call. In general,
 * this is triggered whenever there is a call ended, and the ending was not the result of direct user
 * action.
 *
 * This class will check the network status when handlePreJoinCall is invoked, and transition to
 * [CallLinkPreJoinActionProcessor] as network becomes available again.
 */
class CallLinkNetworkUnavailableActionProcessor(
  webRtcInteractor: WebRtcInteractor
) : GroupNetworkUnavailableActionProcessor(webRtcInteractor) {
  override fun createGroupPreJoinActionProcessor(): GroupPreJoinActionProcessor {
    return CallLinkPreJoinActionProcessor(webRtcInteractor)
  }
}
