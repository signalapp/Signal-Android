package org.thoughtcrime.securesms.service.webrtc

import android.annotation.SuppressLint
import android.os.Build
import android.telecom.DisconnectCause
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.webrtc.AudioOutputOption
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager

/**
 * Wrapper around Jetpack [CallsManager] to manage telecom integration. Maintains a global map of
 * [TelecomCallController] instances associated with their [RecipientId].
 */
@SuppressLint("NewApi")
object AndroidTelecomUtil {

  private val TAG = Log.tag(AndroidTelecomUtil::class.java)
  private val context = AppDependencies.application
  private var systemRejected = false
  private var registered = false
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val callsManager: CallsManager by lazy { CallsManager(context) }

  @JvmStatic
  val telecomSupported: Boolean
    get() {
      if (Build.VERSION.SDK_INT >= 36 && !systemRejected && isTelecomAllowedForDevice()) {
        if (!registered) {
          registerPhoneAccount()
        }
        return registered
      }
      return false
    }

  @JvmStatic
  private val controllers: MutableMap<RecipientId, TelecomCallController> = mutableMapOf()

  @JvmStatic
  fun registerPhoneAccount() {
    if (Build.VERSION.SDK_INT >= 36 && !systemRejected) {
      Log.i(TAG, "Registering with CallsManager")
      try {
        callsManager.registerAppWithTelecom(
          capabilities = CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING,
          backwardsCompatSdkLevel = 37
        )
        Log.i(TAG, "CallsManager registration successful")
        registered = true
      } catch (e: Exception) {
        Log.w(TAG, "Unable to register with CallsManager", e)
        systemRejected = true
      }
    }
  }

  @JvmStatic
  fun addIncomingCall(recipientId: RecipientId, callId: Long, remoteVideoOffer: Boolean): Boolean {
    if (telecomSupported) {
      Log.i(TAG, "addIncomingCall(recipientId=$recipientId, callId=$callId, videoOffer=$remoteVideoOffer)")
      val controller = TelecomCallController(
        context = context,
        recipientId = recipientId,
        callId = callId,
        isVideoCall = remoteVideoOffer,
        isOutgoing = false,
        callsManager = callsManager
      )
      synchronized(controllers) {
        controllers[recipientId] = controller
      }
      scope.launch {
        try {
          Log.i(TAG, "Incoming call controller starting for recipientId=$recipientId callId=$callId")
          controller.start()
          Log.i(TAG, "Incoming call controller scope ended normally for recipientId=$recipientId callId=$callId")
        } catch (e: Exception) {
          Log.w(TAG, "addIncomingCall failed for recipientId=$recipientId callId=$callId", e)
          systemRejected = true
          AppDependencies.signalCallManager.dropCall(callId)
        } finally {
          Log.i(TAG, "Removing incoming controller for recipientId=$recipientId")
          synchronized(controllers) {
            controllers.remove(recipientId)
          }
        }
      }
    }
    return true
  }

  @JvmStatic
  fun addOutgoingCall(recipientId: RecipientId, callId: Long, isVideoCall: Boolean): Boolean {
    if (telecomSupported) {
      Log.i(TAG, "addOutgoingCall(recipientId=$recipientId, callId=$callId, isVideoCall=$isVideoCall)")
      val controller = TelecomCallController(
        context = context,
        recipientId = recipientId,
        callId = callId,
        isVideoCall = isVideoCall,
        isOutgoing = true,
        callsManager = callsManager
      )
      synchronized(controllers) {
        controllers[recipientId] = controller
      }
      scope.launch {
        try {
          Log.i(TAG, "Outgoing call controller starting for recipientId=$recipientId callId=$callId")
          controller.start()
          Log.i(TAG, "Outgoing call controller scope ended normally for recipientId=$recipientId callId=$callId")
        } catch (e: Exception) {
          Log.w(TAG, "addOutgoingCall failed for recipientId=$recipientId callId=$callId", e)
          systemRejected = true
          AppDependencies.signalCallManager.dropCall(callId)
        } finally {
          Log.i(TAG, "Removing outgoing controller for recipientId=$recipientId")
          synchronized(controllers) {
            controllers.remove(recipientId)
          }
        }
      }
    }
    return true
  }

  @JvmStatic
  fun activateCall(recipientId: RecipientId) {
    if (telecomSupported) {
      Log.i(TAG, "activateCall(recipientId=$recipientId) hasController=${synchronized(controllers) { controllers.containsKey(recipientId) }}")
      synchronized(controllers) {
        controllers[recipientId]?.activate()
      }
    }
  }

  @JvmStatic
  @JvmOverloads
  fun terminateCall(recipientId: RecipientId, disconnectCause: Int = DisconnectCause.REMOTE) {
    if (telecomSupported) {
      Log.i(TAG, "terminateCall(recipientId=$recipientId, cause=$disconnectCause) hasController=${synchronized(controllers) { controllers.containsKey(recipientId) }}")
      synchronized(controllers) {
        controllers[recipientId]?.disconnect(disconnectCause)
      }
    }
  }

  @JvmStatic
  fun reject(recipientId: RecipientId) {
    if (telecomSupported) {
      Log.i(TAG, "reject(recipientId=$recipientId) hasController=${synchronized(controllers) { controllers.containsKey(recipientId) }}")
      synchronized(controllers) {
        controllers[recipientId]?.disconnect(DisconnectCause.REJECTED)
      }
    }
  }

  fun getActiveAudioDevice(recipientId: RecipientId): SignalAudioManager.AudioDevice {
    return synchronized(controllers) {
      controllers[recipientId]?.currentAudioDevice ?: SignalAudioManager.AudioDevice.NONE
    }
  }

  fun selectAudioDevice(recipientId: RecipientId, device: SignalAudioManager.AudioDevice) {
    if (telecomSupported) {
      synchronized(controllers) {
        val controller = controllers[recipientId]
        Log.i(TAG, "Selecting audio route: $device controller: ${controller != null}")
        controller?.requestEndpointChange(device)
      }
    }
  }

  @JvmStatic
  fun getAvailableAudioOutputOptions(): List<AudioOutputOption>? {
    if (!telecomSupported) return null
    return synchronized(controllers) {
      controllers.values.firstOrNull()?.getAvailableAudioOutputOptions()
    }
  }

  @JvmStatic
  fun getCurrentEndpointDeviceId(): Int {
    return synchronized(controllers) {
      controllers.values.firstOrNull()?.getCurrentEndpointDeviceId() ?: -1
    }
  }

  @JvmStatic
  fun getCurrentActiveAudioDevice(): SignalAudioManager.AudioDevice {
    return synchronized(controllers) {
      controllers.values.firstOrNull()?.currentAudioDevice ?: SignalAudioManager.AudioDevice.NONE
    }
  }

  @JvmStatic
  fun hasActiveController(): Boolean {
    return synchronized(controllers) { controllers.isNotEmpty() }
  }

  private fun isTelecomAllowedForDevice(): Boolean {
    if (RemoteConfig.internalUser) {
      return !SignalStore.internal.callingDisableTelecom
    }
    return RingRtcDynamicConfiguration.isTelecomAllowedForDevice()
  }
}
