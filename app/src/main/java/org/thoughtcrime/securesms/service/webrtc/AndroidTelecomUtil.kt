package org.thoughtcrime.securesms.service.webrtc

import android.annotation.SuppressLint
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Process
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.DisconnectCause.REJECTED
import android.telecom.DisconnectCause.UNKNOWN
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager

/**
 * Wrapper around various [TelecomManager] methods to make dealing with SDK versions easier. Also
 * maintains a global list of all Signal [AndroidCallConnection]s associated with their [RecipientId].
 * There should really only be one ever, but there may be times when dealing with glare or a busy that two
 * may kick off.
 */
@SuppressLint("NewApi", "InlinedApi")
object AndroidTelecomUtil {

  private val TAG = Log.tag(AndroidTelecomUtil::class.java)
  private val context = AppDependencies.application
  private var systemRejected = false
  private var accountRegistered = false

  @JvmStatic
  val telecomSupported: Boolean
    get() {
      if (Build.VERSION.SDK_INT >= 26 && !systemRejected && isTelecomAllowedForDevice()) {
        if (!accountRegistered) {
          registerPhoneAccount()
        }

        if (accountRegistered) {
          val phoneAccount = ContextCompat.getSystemService(context, TelecomManager::class.java)!!.getPhoneAccount(getPhoneAccountHandle())
          if (phoneAccount != null && phoneAccount.isEnabled) {
            return true
          }
        }
      }
      return false
    }

  @JvmStatic
  val connections: MutableMap<RecipientId, AndroidCallConnection> = mutableMapOf()

  @JvmStatic
  fun registerPhoneAccount() {
    if (Build.VERSION.SDK_INT >= 26 && !systemRejected) {
      Log.i(TAG, "Registering phone account")
      val phoneAccount = PhoneAccount.Builder(getPhoneAccountHandle(), "Signal")
        .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED or PhoneAccount.CAPABILITY_VIDEO_CALLING)
        .build()

      try {
        ContextCompat.getSystemService(context, TelecomManager::class.java)!!.registerPhoneAccount(phoneAccount)
        Log.i(TAG, "Phone account registered successfully")
        accountRegistered = true
      } catch (e: Exception) {
        Log.w(TAG, "Unable to register telecom account", e)
        systemRejected = true
      }
    }
  }

  @JvmStatic
  @RequiresApi(26)
  fun getPhoneAccountHandle(): PhoneAccountHandle {
    return PhoneAccountHandle(ComponentName(context, AndroidCallConnectionService::class.java), context.packageName, Process.myUserHandle())
  }

  @JvmStatic
  fun addIncomingCall(recipientId: RecipientId, callId: Long, remoteVideoOffer: Boolean): Boolean {
    if (telecomSupported) {
      val telecomBundle = bundleOf(
        TelecomManager.EXTRA_INCOMING_CALL_EXTRAS to bundleOf(
          AndroidCallConnectionService.KEY_RECIPIENT_ID to recipientId.serialize(),
          AndroidCallConnectionService.KEY_CALL_ID to callId,
          AndroidCallConnectionService.KEY_VIDEO_CALL to remoteVideoOffer,
          TelecomManager.EXTRA_INCOMING_VIDEO_STATE to if (remoteVideoOffer) VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY
        ),
        TelecomManager.EXTRA_INCOMING_VIDEO_STATE to if (remoteVideoOffer) VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY
      )
      try {
        Log.i(TAG, "Adding incoming call $telecomBundle")
        ContextCompat.getSystemService(context, TelecomManager::class.java)!!.addNewIncomingCall(getPhoneAccountHandle(), telecomBundle)
      } catch (e: SecurityException) {
        Log.w(TAG, "Unable to add incoming call", e)
        systemRejected = true
        return false
      }
    }

    return true
  }

  @JvmStatic
  fun reject(recipientId: RecipientId) {
    if (telecomSupported) {
      connections[recipientId]?.setDisconnected(DisconnectCause(REJECTED))
    }
  }

  @JvmStatic
  fun activateCall(recipientId: RecipientId) {
    if (telecomSupported) {
      connections[recipientId]?.setActive()
    }
  }

  @JvmStatic
  fun terminateCall(recipientId: RecipientId) {
    if (telecomSupported) {
      connections[recipientId]?.let { connection ->
        if (connection.disconnectCause == null) {
          connection.setDisconnected(DisconnectCause(UNKNOWN))
        }
        connection.destroy()
        connections.remove(recipientId)
      }
    }
  }

  @JvmStatic
  fun addOutgoingCall(recipientId: RecipientId, callId: Long, isVideoCall: Boolean): Boolean {
    if (telecomSupported) {
      val telecomBundle = bundleOf(
        TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE to getPhoneAccountHandle(),
        TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE to if (isVideoCall) VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY,
        TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS to bundleOf(
          AndroidCallConnectionService.KEY_RECIPIENT_ID to recipientId.serialize(),
          AndroidCallConnectionService.KEY_CALL_ID to callId,
          AndroidCallConnectionService.KEY_VIDEO_CALL to isVideoCall
        )
      )

      try {
        Log.i(TAG, "Adding outgoing call $telecomBundle")
        ContextCompat.getSystemService(context, TelecomManager::class.java)!!.placeCall(recipientId.generateTelecomE164(), telecomBundle)
      } catch (e: SecurityException) {
        Log.w(TAG, "Unable to add outgoing call", e)
        systemRejected = true
        return false
      }
    }
    return true
  }

  fun selectAudioDevice(recipientId: RecipientId, device: SignalAudioManager.AudioDevice) {
    if (telecomSupported) {
      val connection: AndroidCallConnection? = connections[recipientId]
      Log.i(TAG, "Selecting audio route: $device connection: ${connection != null}")
      if (connection?.callAudioState != null) {
        when (device) {
          SignalAudioManager.AudioDevice.SPEAKER_PHONE -> connection.setAudioRouteIfDifferent(CallAudioState.ROUTE_SPEAKER)
          SignalAudioManager.AudioDevice.BLUETOOTH -> connection.setAudioRouteIfDifferent(CallAudioState.ROUTE_BLUETOOTH)
          SignalAudioManager.AudioDevice.WIRED_HEADSET -> connection.setAudioRouteIfDifferent(CallAudioState.ROUTE_WIRED_HEADSET)
          else -> connection.setAudioRouteIfDifferent(CallAudioState.ROUTE_EARPIECE)
        }
      }
    }
  }

  fun getSelectedAudioDevice(recipientId: RecipientId): SignalAudioManager.AudioDevice {
    if (telecomSupported) {
      val connection: AndroidCallConnection? = connections[recipientId]
      if (connection?.callAudioState != null) {
        return when (connection.callAudioState.route) {
          CallAudioState.ROUTE_SPEAKER -> SignalAudioManager.AudioDevice.SPEAKER_PHONE
          CallAudioState.ROUTE_BLUETOOTH -> SignalAudioManager.AudioDevice.BLUETOOTH
          CallAudioState.ROUTE_WIRED_HEADSET -> SignalAudioManager.AudioDevice.WIRED_HEADSET
          else -> SignalAudioManager.AudioDevice.EARPIECE
        }
      }
    }
    return SignalAudioManager.AudioDevice.NONE
  }

  private fun isTelecomAllowedForDevice(): Boolean {
    if (RemoteConfig.internalUser) {
      return !SignalStore.internal.callingDisableTelecom()
    }
    return RingRtcDynamicConfiguration.isTelecomAllowedForDevice()
  }
}

@RequiresApi(26)
private fun Connection.setAudioRouteIfDifferent(newRoute: Int) {
  if (callAudioState.route != newRoute) {
    setAudioRoute(newRoute)
  }
}

private fun RecipientId.generateTelecomE164(): Uri {
  val pseudoNumber = toLong().toString().padEnd(10, '0').replaceRange(3..5, "555")
  return Uri.fromParts("tel", "+1$pseudoNumber", null)
}
