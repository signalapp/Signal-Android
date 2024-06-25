package org.thoughtcrime.securesms.service.webrtc

import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Signal implementation of the Android telecom [ConnectionService]. The system binds to this service
 * when we inform the [TelecomManager] of a new incoming or outgoing call. It'll then call the appropriate
 * create/failure method to let us know how to proceed.
 */
@RequiresApi(26)
class AndroidCallConnectionService : ConnectionService() {

  override fun onCreateIncomingConnection(
    connectionManagerPhoneAccount: PhoneAccountHandle?,
    request: ConnectionRequest
  ): Connection {
    val (recipientId: RecipientId, callId: Long, isVideoCall: Boolean) = request.getOurExtras()

    Log.i(TAG, "onCreateIncomingConnection($recipientId)")
    val recipient = Recipient.resolved(recipientId)
    val displayName = recipient.getDisplayName(this)
    val connection = AndroidCallConnection(
      context = applicationContext,
      recipientId = recipientId,
      isOutgoing = false,
      isVideoCall = isVideoCall
    ).apply {
      setInitializing()
      if (SignalStore.settings.messageNotificationsPrivacy.isDisplayContact && recipient.e164.isPresent) {
        setAddress(Uri.fromParts("tel", recipient.e164.get(), null), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
      }
      videoState = request.videoState
      extras = request.extras
      setRinging()
    }
    AndroidTelecomUtil.connections[recipientId] = connection
    AppDependencies.signalCallManager.setTelecomApproved(callId, recipientId)

    return connection
  }

  override fun onCreateIncomingConnectionFailed(
    connectionManagerPhoneAccount: PhoneAccountHandle?,
    request: ConnectionRequest
  ) {
    val (recipientId: RecipientId, callId: Long) = request.getOurExtras()

    Log.i(TAG, "onCreateIncomingConnectionFailed($recipientId)")
    AppDependencies.signalCallManager.dropCall(callId)
  }

  override fun onCreateOutgoingConnection(
    connectionManagerPhoneAccount: PhoneAccountHandle?,
    request: ConnectionRequest
  ): Connection {
    val (recipientId: RecipientId, callId: Long, isVideoCall: Boolean) = request.getOurExtras()

    Log.i(TAG, "onCreateOutgoingConnection($recipientId)")
    val connection = AndroidCallConnection(
      context = applicationContext,
      recipientId = recipientId,
      isOutgoing = true,
      isVideoCall = isVideoCall
    ).apply {
      videoState = request.videoState
      extras = request.extras
      setDialing()
    }
    AndroidTelecomUtil.connections[recipientId] = connection
    AppDependencies.signalCallManager.setTelecomApproved(callId, recipientId)

    return connection
  }

  override fun onCreateOutgoingConnectionFailed(
    connectionManagerPhoneAccount: PhoneAccountHandle?,
    request: ConnectionRequest
  ) {
    val (recipientId: RecipientId, callId: Long) = request.getOurExtras()

    Log.i(TAG, "onCreateOutgoingConnectionFailed($recipientId)")
    AppDependencies.signalCallManager.dropCall(callId)
  }

  companion object {
    private val TAG: String = Log.tag(AndroidCallConnectionService::class.java)
    const val KEY_RECIPIENT_ID = "org.thoughtcrime.securesms.RECIPIENT_ID"
    const val KEY_CALL_ID = "org.thoughtcrime.securesms.CALL_ID"
    const val KEY_VIDEO_CALL = "org.thoughtcrime.securesms.VIDEO_CALL"
  }

  private fun ConnectionRequest.getOurExtras(): ServiceExtras {
    val ourExtras: Bundle = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS) ?: extras

    val recipientId: RecipientId = RecipientId.from(ourExtras.getString(KEY_RECIPIENT_ID)!!)
    val callId: Long = ourExtras.getLong(KEY_CALL_ID)
    val isVideoCall: Boolean = ourExtras.getBoolean(KEY_VIDEO_CALL, false)

    return ServiceExtras(recipientId, callId, isVideoCall)
  }

  private data class ServiceExtras(val recipientId: RecipientId, val callId: Long, val isVideoCall: Boolean)
}
