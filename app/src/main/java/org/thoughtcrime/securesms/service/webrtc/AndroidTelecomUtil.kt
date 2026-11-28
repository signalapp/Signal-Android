package org.thoughtcrime.securesms.service.webrtc

import android.annotation.SuppressLint
import android.media.AudioManager
import android.os.Build
import android.telecom.DisconnectCause
import androidx.core.telecom.CallException
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.components.webrtc.AudioOutputOption
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TelephonyUtil
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import java.util.concurrent.atomic.AtomicReference

/**
 * Wrapper around Jetpack [CallsManager] to manage telecom integration. Maintains a global map of
 * [TelecomCallController] instances associated with their [RecipientId].
 */
@SuppressLint("NewApi")
object AndroidTelecomUtil {

  private val TAG = Log.tag(AndroidTelecomUtil::class.java)

  /** Our call audio routing and picker rely on the API 31 communication device APIs. */
  private const val MIN_SDK = 31

  private const val BACKWARDS_COMPAT_SDK_LEVEL = 36

  private val context = AppDependencies.application

  @Volatile
  private var systemRejected = false

  @Volatile
  private var registered = false

  private val appliedSystemMuteRef = AtomicReference<AppliedSystemMute?>(null)

  val appliedSystemMute: Boolean?
    get() = appliedSystemMuteRef.get()?.muted

  /** Serial so each call's flow collectors and command loop never run concurrently and can share plain local state. */
  private val scope = CoroutineScope(SupervisorJob() + SignalDispatchers.Default.limitedParallelism(1, "AndroidTelecomUtil"))

  private val callsManager: CallsManager by lazy { CallsManager(context) }

  fun setAppliedSystemMute(owner: Any, muted: Boolean) {
    appliedSystemMuteRef.set(AppliedSystemMute(owner, muted))
  }

  fun clearAppliedSystemMute(owner: Any) {
    appliedSystemMuteRef.updateAndGet { if (it?.owner === owner) null else it }
  }

  @JvmStatic
  val telecomSupported: Boolean
    get() {
      if (isEligible()) {
        if (!registered) {
          registerPhoneAccount()
        }
        return registered
      }
      return false
    }

  private fun isEligible(): Boolean {
    return Build.VERSION.SDK_INT >= maxOf(MIN_SDK, RemoteConfig.telecomMinSdkVersion) && !systemRejected && isTelecomAllowedForDevice()
  }

  @JvmStatic
  private val controllers: MutableMap<RecipientId, TelecomCallController> = mutableMapOf()

  @JvmStatic
  @Synchronized
  fun registerPhoneAccount() {
    if (registered) {
      return
    }

    if (isEligible()) {
      Log.i(TAG, "Registering with CallsManager")
      try {
        callsManager.registerAppWithTelecom(
          capabilities = CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING,
          backwardsCompatSdkLevel = BACKWARDS_COMPAT_SDK_LEVEL
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
  fun addIncomingCall(recipientId: RecipientId, callId: Long, remoteVideoOffer: Boolean) {
    addCall(recipientId, callId, remoteVideoOffer, isOutgoing = false, preferredEndpoint = null)
  }

  @JvmStatic
  fun addOutgoingCall(recipientId: RecipientId, callId: Long, isVideoCall: Boolean, preferredEndpoint: SignalAudioManager.AudioDevice?) {
    addCall(recipientId, callId, isVideoCall, isOutgoing = true, preferredEndpoint = preferredEndpoint)
  }

  private fun addCall(recipientId: RecipientId, callId: Long, isVideoCall: Boolean, isOutgoing: Boolean, preferredEndpoint: SignalAudioManager.AudioDevice?) {
    val direction = if (isOutgoing) "outgoing" else "incoming"
    Log.i(TAG, "addCall($direction, recipientId=$recipientId, callId=$callId, isVideoCall=$isVideoCall)")
    val controller = TelecomCallController(
      context = context,
      recipientId = recipientId,
      callId = callId,
      isVideoCall = isVideoCall,
      isOutgoing = isOutgoing,
      callsManager = callsManager,
      preferredEndpoint = preferredEndpoint
    )
    synchronized(controllers) {
      controllers.remove(recipientId)
      controllers[recipientId] = controller
    }
    scope.launch {
      try {
        Log.i(TAG, "Starting $direction call controller for recipientId=$recipientId callId=$callId")
        when (val result = controller.start()) {
          is TelecomCallController.StartResult.Ended -> Log.i(TAG, "The $direction call controller scope ended normally for recipientId=$recipientId callId=$callId")
          is TelecomCallController.StartResult.Failed -> {
            Log.w(TAG, "The $direction call failed for recipientId=$recipientId callId=$callId afterApproval=${result.afterApproval}", result.error)
            val conflict = isCallConflict(result.error)
            if (conflict) {
              Log.w(TAG, "Call conflicts with another call, keeping telecom enabled")
            } else if (result.afterApproval) {
              Log.w(TAG, "Telecom failed after approving the call, keeping telecom enabled")
            } else {
              systemRejected = true
            }
            AppDependencies.signalCallManager.dropCall(callId, !isOutgoing && conflict)
          }
        }
      } finally {
        synchronized(controllers) {
          if (controllers[recipientId] == controller) {
            Log.i(TAG, "Removing $direction controller for recipientId=$recipientId callId=$callId")
            controllers.remove(recipientId)
          } else {
            Log.i(TAG, "Not removing $direction controller for recipientId=$recipientId callId=$callId, replaced by a newer call")
          }
        }
      }
    }
  }

  private fun isCallConflict(error: Exception): Boolean {
    if (error !is CallException) {
      return false
    }

    return when (error.code) {
      CallException.ERROR_CANNOT_HOLD_CURRENT_ACTIVE_CALL, CallException.ERROR_CALL_NOT_PERMITTED_AT_PRESENT_TIME -> true
      CallException.ERROR_UNKNOWN -> Build.VERSION.SDK_INT <= BACKWARDS_COMPAT_SDK_LEVEL && isAnotherCallInProgress()
      else -> false
    }
  }

  /** The legacy backend reports every refusal as [CallException.ERROR_UNKNOWN], so check for the call that is in the way. */
  private fun isAnotherCallInProgress(): Boolean {
    val audioMode = AppDependencies.androidCallAudioManager.mode
    val pstnBusy = TelephonyUtil.isAnyPstnLineBusy(context)
    Log.i(TAG, "Checking for another call after a legacy refusal: audioMode=$audioMode pstnBusy=$pstnBusy")
    return pstnBusy || audioMode != AudioManager.MODE_NORMAL
  }

  @JvmStatic
  fun activateCall(recipientId: RecipientId, callId: Long, acceptWithVideo: Boolean) {
    Log.i(TAG, "activateCall(recipientId=$recipientId, callId=$callId, acceptWithVideo=$acceptWithVideo)")
    synchronized(controllers) {
      controllerFor(recipientId, callId)?.activate(acceptWithVideo)
    }
  }

  @JvmStatic
  @JvmOverloads
  fun terminateCall(recipientId: RecipientId, callId: Long, disconnectCause: Int = DisconnectCause.REMOTE) {
    Log.i(TAG, "terminateCall(recipientId=$recipientId, callId=$callId, cause=$disconnectCause)")
    synchronized(controllers) {
      controllerFor(recipientId, callId)?.disconnect(disconnectCause)
    }
  }

  @JvmStatic
  fun reject(recipientId: RecipientId, callId: Long) {
    Log.i(TAG, "reject(recipientId=$recipientId, callId=$callId)")
    synchronized(controllers) {
      controllerFor(recipientId, callId)?.disconnect(DisconnectCause.REJECTED)
    }
  }

  private fun controllerFor(recipientId: RecipientId, callId: Long): TelecomCallController? {
    val controller = controllers[recipientId]
    return when {
      controller == null -> {
        Log.i(TAG, "No controller for recipientId=$recipientId callId=$callId")
        null
      }
      controller.callId != callId -> {
        Log.w(TAG, "Ignoring request for callId=$callId, controller for recipientId=$recipientId is callId=${controller.callId}")
        null
      }
      else -> controller
    }
  }

  fun getActiveAudioDevice(recipientId: RecipientId): SignalAudioManager.AudioDevice {
    return synchronized(controllers) {
      controllers[recipientId]?.currentAudioDevice ?: SignalAudioManager.AudioDevice.NONE
    }
  }

  @JvmStatic
  fun getActiveAudioDeviceForLogging(): SignalAudioManager.AudioDevice {
    return synchronized(controllers) {
      controllers.values.firstOrNull()?.currentAudioDevice ?: SignalAudioManager.AudioDevice.NONE
    }
  }

  fun selectAudioDevice(recipientId: RecipientId, device: SignalAudioManager.AudioDevice) {
    synchronized(controllers) {
      val controller = controllers[recipientId]
      Log.i(TAG, "Selecting audio route: $device controller: ${controller != null}")
      controller?.requestEndpointChange(device)
    }
  }

  fun selectAudioDevice(recipientId: RecipientId, deviceId: Int) {
    synchronized(controllers) {
      val controller = controllers[recipientId]
      Log.i(TAG, "Selecting audio route by id: $deviceId controller: ${controller != null}")
      controller?.requestEndpointChange(deviceId)
    }
  }

  fun selectDefaultAudioDevice(recipientId: RecipientId, device: SignalAudioManager.AudioDevice) {
    synchronized(controllers) {
      val controller = controllers[recipientId]
      Log.i(TAG, "Selecting default audio route: $device controller: ${controller != null}")
      controller?.requestDefaultEndpoint(device)
    }
  }

  @JvmStatic
  fun getAvailableAudioOutputOptions(): List<AudioOutputOption>? {
    return synchronized(controllers) {
      controllers.values.lastOrNull()?.getAvailableAudioOutputOptions()
    }
  }

  @JvmStatic
  fun getCurrentEndpointDeviceId(): Int {
    return synchronized(controllers) {
      controllers.values.lastOrNull()?.getCurrentEndpointDeviceId() ?: -1
    }
  }

  private fun isTelecomAllowedForDevice(): Boolean {
    if (RemoteConfig.internalUser) {
      return SignalStore.internal.callingUseTelecom
    }

    return RingRtcDynamicConfiguration.isTelecomAllowedForDevice()
  }
}

private class AppliedSystemMute(val owner: Any, val muted: Boolean)
