package org.thoughtcrime.securesms.service.webrtc.state

import org.thoughtcrime.securesms.recipients.Recipient
import org.webrtc.PeerConnection

/**
 * Information specific to setting up a call.
 */
data class CallSetupState(
  var isEnableVideoOnCreate: Boolean = false,
  var isRemoteVideoOffer: Boolean = false,
  var isAcceptWithVideo: Boolean = false,
  @get:JvmName("hasSentJoinedMessage") var sentJoinedMessage: Boolean = false,
  @get:JvmName("shouldRingGroup") var ringGroup: Boolean = true,
  var ringId: Long = NO_RING,
  var ringerRecipient: Recipient = Recipient.UNKNOWN,
  @get:JvmName("shouldWaitForTelecomApproval") var waitForTelecom: Boolean = false,
  @get:JvmName("isTelecomApproved") var telecomApproved: Boolean = false,
  var iceServers: MutableList<PeerConnection.IceServer> = mutableListOf(),
  @get:JvmName("isAlwaysTurnServers") var alwaysTurnServers: Boolean = false
) {

  fun duplicate(): CallSetupState {
    return copy()
  }

  companion object {
    const val NO_RING = 0L
  }
}
