/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

/**
 * Factory for generating action processors for call links and groups.
 */
sealed interface MultiPeerActionProcessorFactory {

  fun createPreJoinActionProcessor(webRtcInteractor: WebRtcInteractor): GroupPreJoinActionProcessor
  fun createJoiningActionProcessor(webRtcInteractor: WebRtcInteractor): GroupJoiningActionProcessor
  fun createConnectedActionProcessor(webRtcInteractor: WebRtcInteractor): GroupConnectedActionProcessor

  fun createNetworkUnavailableActionProcessor(webRtcInteractor: WebRtcInteractor): GroupNetworkUnavailableActionProcessor {
    return GroupNetworkUnavailableActionProcessor(this, webRtcInteractor)
  }

  object GroupActionProcessorFactory : MultiPeerActionProcessorFactory {
    override fun createPreJoinActionProcessor(webRtcInteractor: WebRtcInteractor): GroupPreJoinActionProcessor {
      return GroupPreJoinActionProcessor(this, webRtcInteractor)
    }

    override fun createJoiningActionProcessor(webRtcInteractor: WebRtcInteractor): GroupJoiningActionProcessor {
      return GroupJoiningActionProcessor(this, webRtcInteractor)
    }

    override fun createConnectedActionProcessor(webRtcInteractor: WebRtcInteractor): GroupConnectedActionProcessor {
      return GroupConnectedActionProcessor(this, webRtcInteractor)
    }
  }

  object CallLinkActionProcessorFactory : MultiPeerActionProcessorFactory {
    override fun createPreJoinActionProcessor(webRtcInteractor: WebRtcInteractor): GroupPreJoinActionProcessor {
      return CallLinkPreJoinActionProcessor(this, webRtcInteractor)
    }

    override fun createJoiningActionProcessor(webRtcInteractor: WebRtcInteractor): GroupJoiningActionProcessor {
      return CallLinkJoiningActionProcessor(this, webRtcInteractor)
    }

    override fun createConnectedActionProcessor(webRtcInteractor: WebRtcInteractor): GroupConnectedActionProcessor {
      return CallLinkConnectedActionProcessor(this, webRtcInteractor)
    }
  }
}
