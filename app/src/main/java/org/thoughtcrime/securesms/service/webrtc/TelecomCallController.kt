package org.thoughtcrime.securesms.service.webrtc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.telecom.DisconnectCause
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallException
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.ServiceUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Scrubber
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.AudioOutputOption
import org.thoughtcrime.securesms.components.webrtc.v2.CallIntent
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.webrtc.audio.AudioDeviceMapping
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import java.util.concurrent.CopyOnWriteArrayList

sealed class TelecomCommand {
  data class Activate(val acceptWithVideo: Boolean) : TelecomCommand()
  data class Disconnect(val cause: Int) : TelecomCommand()
  data class ChangeEndpoint(val device: SignalAudioManager.AudioDevice, val isDefault: Boolean = false, val identifier: ParcelUuid? = null) : TelecomCommand()
}

@RequiresApi(26)
class TelecomCallController(
  private val context: Context,
  private val recipientId: RecipientId,
  val callId: Long,
  private val isVideoCall: Boolean,
  private val isOutgoing: Boolean,
  private val callsManager: CallsManager,
  @Volatile private var preferredEndpoint: SignalAudioManager.AudioDevice? = null
) {

  companion object {
    private val TAG: String = Log.tag(TelecomCallController::class.java)
    private const val PREFERRED_ENDPOINT_SETTLE_MS = 2000L

    private val HEADSET_DEVICES = setOf(SignalAudioManager.AudioDevice.BLUETOOTH, SignalAudioManager.AudioDevice.WIRED_HEADSET)

    /** The name core-telecom's legacy backend gives a bluetooth endpoint it can't read, and then can only route to generically. */
    private const val UNNAMED_BLUETOOTH_ENDPOINT = "Bluetooth Device"

    @VisibleForTesting
    fun endpointKey(endpoint: CallEndpointCompat, endpoints: List<CallEndpointCompat>): String {
      val device = endpoint.type.toAudioDevice()
      return if (device == SignalAudioManager.AudioDevice.BLUETOOTH && endpoints.none { it.isUnnamedBluetooth() }) {
        "endpoint:${endpoint.identifier}"
      } else {
        "device:$device"
      }
    }

    private fun CallEndpointCompat.isUnnamedBluetooth(): Boolean {
      return type == CallEndpointCompat.TYPE_BLUETOOTH && name.toString() == UNNAMED_BLUETOOTH_ENDPOINT
    }

    @VisibleForTesting
    fun unnamedBluetoothProductName(endpoints: List<CallEndpointCompat>, productNames: () -> List<String>): String? {
      if (endpoints.count { it.isUnnamedBluetooth() } != 1) {
        return null
      }
      return productNames().distinct().singleOrNull()
    }

    @VisibleForTesting
    fun shouldSkipDefaultEndpoint(command: TelecomCommand.ChangeEndpoint, available: Set<SignalAudioManager.AudioDevice>): Boolean {
      return command.isDefault && command.device !in HEADSET_DEVICES && available.any { it in HEADSET_DEVICES }
    }

    @VisibleForTesting
    fun resolveEndpoint(command: TelecomCommand.ChangeEndpoint, endpoints: List<CallEndpointCompat>): EndpointResolution {
      if (shouldSkipDefaultEndpoint(command, endpoints.map { it.type.toAudioDevice() }.toSet())) {
        return EndpointResolution.Skip
      }
      if (endpoints.isEmpty()) {
        return EndpointResolution.Hold
      }
      val target = endpoints.firstOrNull { command.identifier != null && it.identifier == command.identifier }
        ?: endpoints.firstOrNull { it.type.toAudioDevice() == command.device }
      return target?.let { EndpointResolution.Request(it) } ?: EndpointResolution.NotFound
    }
  }

  sealed interface EndpointResolution {
    data object Skip : EndpointResolution
    data object Hold : EndpointResolution
    data object NotFound : EndpointResolution
    data class Request(val endpoint: CallEndpointCompat) : EndpointResolution
  }

  private val commandChannel = Channel<TelecomCommand>(Channel.BUFFERED)

  @Volatile
  var currentAudioDevice: SignalAudioManager.AudioDevice = SignalAudioManager.AudioDevice.NONE
    private set

  @Volatile
  private var cachedEndpoints: List<CallEndpointCompat> = emptyList()

  @Volatile
  private var currentEndpoint: CallEndpointCompat? = null

  private val endpointIds = mutableMapOf<String, Int>()

  @Volatile
  private var disconnected: Boolean = false

  @Volatile
  private var failure: Exception? = null

  @Volatile
  private var approved: Boolean = false

  private var answeredByTelecomCallType: Int? = null

  private val callJobs: MutableList<Job> = CopyOnWriteArrayList()

  fun getAvailableAudioOutputOptions(): List<AudioOutputOption> {
    val endpoints = cachedEndpoints
    val unnamedBluetoothName by lazy { unnamedBluetoothName(endpoints) }
    return endpoints
      .filterNot { it.type.toAudioDevice() == SignalAudioManager.AudioDevice.NONE }
      .distinctBy { endpointKey(it, endpoints) }
      .map {
        val name = if (it.isUnnamedBluetooth()) unnamedBluetoothName else it.name.toString()
        AudioOutputOption(name, it.type.toAudioDevice(), endpointId(it, endpoints))
      }
  }

  fun getCurrentEndpointDeviceId(): Int {
    return currentEndpoint?.let { endpointId(it, cachedEndpoints) } ?: -1
  }

  private fun endpointId(endpoint: CallEndpointCompat, endpoints: List<CallEndpointCompat>): Int {
    val key = endpointKey(endpoint, endpoints)
    return synchronized(endpointIds) { endpointIds.getOrPut(key) { endpointIds.size } }
  }

  private fun unnamedBluetoothName(endpoints: List<CallEndpointCompat>): String {
    return unnamedBluetoothProductName(endpoints) { bluetoothProductNames() } ?: context.getString(R.string.WebRtcAudioOutputToggle__bluetooth)
  }

  private fun bluetoothProductNames(): List<String> {
    if (Build.VERSION.SDK_INT < 31) {
      return emptyList()
    }
    return AppDependencies.androidCallAudioManager.availableCommunicationDevices
      .filter { AudioDeviceMapping.fromPlatformType(it.type) == SignalAudioManager.AudioDevice.BLUETOOTH }
      .map { it.productName.toString() }
  }

  fun activate(acceptWithVideo: Boolean) {
    Log.i(TAG, "activate() recipientId=$recipientId callId=$callId")
    commandChannel.trySend(TelecomCommand.Activate(acceptWithVideo))
  }

  fun disconnect(cause: Int) {
    if (disconnected) {
      Log.i(TAG, "disconnect(cause=$cause) already disconnected, ignoring")
      return
    }
    disconnected = true

    Log.i(TAG, "disconnect(cause=$cause) recipientId=$recipientId callId=$callId")
    commandChannel.trySend(TelecomCommand.Disconnect(cause))
  }

  fun requestEndpointChange(device: SignalAudioManager.AudioDevice) {
    Log.i(TAG, "requestEndpointChange($device) recipientId=$recipientId")
    preferredEndpoint = null
    commandChannel.trySend(TelecomCommand.ChangeEndpoint(device))
  }

  fun requestEndpointChange(deviceId: Int) {
    val endpoints = cachedEndpoints
    val endpoint = endpoints.firstOrNull { endpointId(it, endpoints) == deviceId }
    if (endpoint == null) {
      Log.w(TAG, "requestEndpointChange(deviceId=$deviceId) no matching endpoint for recipientId=$recipientId")
      return
    }
    val device = endpoint.type.toAudioDevice()
    Log.i(TAG, "requestEndpointChange(deviceId=$deviceId) -> $device ${Scrubber.hashForLog(endpoint.name)} recipientId=$recipientId")
    preferredEndpoint = null
    commandChannel.trySend(TelecomCommand.ChangeEndpoint(device, identifier = endpoint.identifier))
  }

  fun requestDefaultEndpoint(device: SignalAudioManager.AudioDevice) {
    val preferred = preferredEndpoint
    if (preferred != null) {
      Log.i(TAG, "requestDefaultEndpoint($device) ignored, preferred endpoint $preferred is pending for recipientId=$recipientId")
      return
    }
    Log.i(TAG, "requestDefaultEndpoint($device) recipientId=$recipientId")
    commandChannel.trySend(TelecomCommand.ChangeEndpoint(device, isDefault = true))
  }

  /**
   * Adds the call to Telecom and suspends until Telecom is done with it.
   */
  suspend fun start(): StartResult {
    return try {
      addCallToTelecom()
      failure?.let { StartResult.Failed(it, approved) } ?: StartResult.Ended
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      StartResult.Failed(e, approved)
    }
  }

  private suspend fun addCallToTelecom() {
    val recipient = Recipient.resolved(recipientId)
    val displayContact = SignalStore.settings.messageNotificationsPrivacy.isDisplayContact
    val displayName = if (displayContact) recipient.getDisplayName(context) else context.getString(R.string.Recipient_signal_call)

    // Handsfree devices reduce the handle to dialable characters, so only an E164 survives to be displayed or phonebook matched.
    val (address, handleMode) = if (displayContact && recipient.shouldShowE164) {
      Uri.fromParts("tel", recipient.requireE164(), null) to HandleMode.TEL
    } else {
      Uri.fromParts("sip", "signalcall", null) to HandleMode.SIP_OPAQUE
    }

    val direction = if (isOutgoing) CallAttributesCompat.DIRECTION_OUTGOING else CallAttributesCompat.DIRECTION_INCOMING
    val callType = if (isVideoCall) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL

    val attributes = CallAttributesCompat(
      displayName = displayName,
      address = address,
      direction = direction,
      callType = callType,
      callCapabilities = 0
    )

    Log.i(TAG, "start() recipientId=$recipientId callId=$callId isOutgoing=$isOutgoing isVideo=$isVideoCall handleMode=${handleMode.logValue}")

    callsManager.addCall(
      callAttributes = attributes,
      onAnswer = { callType -> onAnswer(callType) },
      onDisconnect = { cause -> onDisconnect(cause) },
      onSetActive = { onSetActive() },
      onSetInactive = { onSetInactive() }
    ) {
      try {
        runCallControl()
      } catch (e: Exception) {
        onCallControlFailure(e)
      }
    }
  }

  private fun CallControlScope.launchGuarded(block: suspend CoroutineScope.() -> Unit): Job {
    val job = launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onCallControlFailure(e)
      }
    }
    callJobs += job
    return job
  }

  private fun CallControlScope.onCallControlFailure(e: Exception) {
    if (failure != null) {
      Log.w(TAG, "Additional call control failure for callId=$callId", e)
      return
    }

    Log.w(TAG, "Call control failed for callId=$callId, disconnecting from telecom", e)
    failure = e
    commandChannel.close()
    launch {
      disconnect(DisconnectCause(DisconnectCause.ERROR))
      callJobs.forEach { it.cancel() }
    }
  }

  private fun CallControlScope.runCallControl() {
    Log.i(TAG, "addCall block entered, callControlScope active for callId=$callId")

    approved = true
    AppDependencies.signalCallManager.setTelecomApproved(callId, recipientId)
    Log.i(TAG, "setTelecomApproved fired for callId=$callId recipientId=$recipientId")

    var needToResetAudioRoute = isOutgoing && !isVideoCall
    var initialEndpoint: SignalAudioManager.AudioDevice? = null
    var preferredRequested = false
    var preferredApplied = false
    var activated = false
    var pendingEndpoint: TelecomCommand.ChangeEndpoint? = null

    val endpointJob = launchGuarded {
      currentCallEndpoint.collect { endpoint ->
        val activeDevice = endpoint.type.toAudioDevice()
        Log.i(TAG, "currentCallEndpoint changed: ${Scrubber.hashForLog(endpoint.name)} (type=${endpoint.type}) -> $activeDevice")
        currentAudioDevice = activeDevice
        currentEndpoint = endpoint

        val available = cachedEndpoints.map { it.type.toAudioDevice() }.toSet()
        AppDependencies.signalCallManager.onAudioDeviceChanged(activeDevice, available)

        val preferred = preferredEndpoint
        if (preferred != null && preferredRequested) {
          if (activeDevice == preferred) {
            preferredApplied = true
          } else if (preferredApplied && activeDevice == SignalAudioManager.AudioDevice.EARPIECE) {
            Log.i(TAG, "Telecom moved to EARPIECE, re-applying preferred endpoint $preferred for callId=$callId")
            preferredEndpoint = null
            commandChannel.trySend(TelecomCommand.ChangeEndpoint(preferred))
          }
        }

        if (needToResetAudioRoute) {
          if (initialEndpoint == null) {
            initialEndpoint = activeDevice
          } else if (activeDevice == SignalAudioManager.AudioDevice.SPEAKER_PHONE) {
            Log.i(TAG, "Resetting audio route from SPEAKER_PHONE to $initialEndpoint")
            val resetTarget = cachedEndpoints.firstOrNull { it.type.toAudioDevice() == initialEndpoint }
            if (resetTarget != null) {
              requestEndpointChange(resetTarget)
            }
            needToResetAudioRoute = false
          }
        }
      }
    }

    val muteJob = launchGuarded {
      isMuted.distinctUntilChanged().collect { muted ->
        val applied = AndroidTelecomUtil.appliedSystemMute
        val systemMuted = AppDependencies.androidCallAudioManager.isMicrophoneMute
        if (applied == null || muted == applied || muted != systemMuted) {
          Log.i(TAG, "isMuted=$muted ignored for callId=$callId applied=$applied system=$systemMuted source=telecom")
        } else {
          Log.i(TAG, "isMuted changed: $muted for callId=$callId source=telecom")
          AppDependencies.signalCallManager.setMuteAudio(muted)
        }
      }
    }

    val availableEndpointsJob = launchGuarded {
      availableEndpoints.collect { endpoints ->
        cachedEndpoints = endpoints
        val available = endpoints.map { it.type.toAudioDevice() }.toSet()
        Log.i(TAG, "availableEndpoints changed: $available (${endpoints.size} endpoints)")
        AppDependencies.signalCallManager.onAudioDeviceChanged(currentAudioDevice, available)

        val preferred = preferredEndpoint
        if (preferred != null && !preferredRequested && preferred in available) {
          Log.i(TAG, "Applying preferred endpoint $preferred for callId=$callId")
          preferredRequested = true
          needToResetAudioRoute = false
          pendingEndpoint = null
          commandChannel.trySend(TelecomCommand.ChangeEndpoint(preferred))
        }

        val pending = pendingEndpoint
        if (pending != null && endpoints.isNotEmpty()) {
          Log.i(TAG, "Applying endpoint ${pending.device} requested before endpoints were available for callId=$callId")
          pendingEndpoint = null
          commandChannel.trySend(pending)
        }
      }
    }

    launchGuarded {
      for (command in commandChannel) {
        when (command) {
          is TelecomCommand.Activate -> {
            if (activated) {
              Log.i(TAG, "Already active, ignoring activate for callId=$callId")
              continue
            }
            val telecomCallType = answeredByTelecomCallType
            val result = if (isOutgoing) {
              setActive()
            } else if (telecomCallType != null) {
              Log.i(TAG, "Already answered by telecom with callType=$telecomCallType, skipping answer for callId=$callId")
              CallControlResult.Success()
            } else {
              val callType = if (isVideoCall && command.acceptWithVideo) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL
              answer(callType)
            }
            Log.i(TAG, "activate result: $result acceptWithVideo=${command.acceptWithVideo}")
            needToResetAudioRoute = false
            if (result is CallControlResult.Error) {
              onCallControlFailure(CallException(result.errorCode))
            } else {
              activated = true
              if (!isOutgoing && !command.acceptWithVideo && (isVideoCall || telecomCallType == CallAttributesCompat.CALL_TYPE_VIDEO_CALL)) {
                val callTypeResult = requestCallType(CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
                Log.i(TAG, "requestCallType(audio) result: $callTypeResult for callId=$callId")
              }
              if (preferredEndpoint != null) {
                launchGuarded {
                  delay(PREFERRED_ENDPOINT_SETTLE_MS)
                  Log.i(TAG, "Preferred endpoint settle window ended for callId=$callId")
                  preferredEndpoint = null
                }
              }
            }
          }
          is TelecomCommand.Disconnect -> {
            val result = disconnect(DisconnectCause(command.cause))
            Log.i(TAG, "disconnect result: $result")
            break
          }
          is TelecomCommand.ChangeEndpoint -> {
            needToResetAudioRoute = false
            pendingEndpoint = null
            val endpoints = cachedEndpoints
            when (val resolution = resolveEndpoint(command, endpoints)) {
              EndpointResolution.Skip -> Log.i(TAG, "Skipping default endpoint ${command.device}, a headset is available: ${endpoints.map { it.type.toAudioDevice() }} for callId=$callId")
              EndpointResolution.Hold -> {
                Log.i(TAG, "No endpoints yet, holding ${command.device} (default=${command.isDefault}) until they arrive for callId=$callId")
                pendingEndpoint = command
              }
              EndpointResolution.NotFound -> Log.w(TAG, "No endpoint found for device: ${command.device}, available: ${endpoints.map { it.type.toAudioDevice() }}")
              is EndpointResolution.Request -> Log.i(TAG, "requestEndpointChange(${command.device}) result: ${requestEndpointChange(resolution.endpoint)}")
            }
          }
        }
      }

      Log.i(TAG, "Command loop ended, cancelling flow collectors for callId=$callId")
      endpointJob.cancel()
      muteJob.cancel()
      availableEndpointsJob.cancel()
    }
  }

  private fun onAnswer(callType: Int) {
    val hasRecordAudio = Permissions.hasAll(context, android.Manifest.permission.RECORD_AUDIO)
    Log.i(TAG, "onAnswer(callType=$callType) recipientId=$recipientId hasRecordAudio=$hasRecordAudio")
    answeredByTelecomCallType = callType

    if (hasRecordAudio) {
      AppDependencies.signalCallManager.acceptCall(false)
      openCallScreenIfUnlocked()
    } else {
      Log.w(TAG, "Answered without RECORD_AUDIO permission for callId=$callId, opening the call screen to request it before accepting")
      ActiveCallManager.promptToOpenCallScreen(context, R.string.CallNotificationBuilder__to_answer_this_call_allow_signal_access_to_your_microphone, answerCall = true)
    }
  }

  private fun openCallScreenIfUnlocked() {
    if (ServiceUtil.getKeyguardManager(context).isKeyguardLocked) {
      Log.i(TAG, "Answered by telecom while locked, leaving the call screen closed for callId=$callId")
      return
    }

    Log.i(TAG, "Answered by telecom while unlocked, opening the call screen for callId=$callId")
    try {
      context.startActivity(CallIntent.Builder(context).withAddedIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK).withAction(CallIntent.Action.SHOW_ANSWERING).build())
    } catch (e: Exception) {
      Log.w(TAG, "Unable to open the call screen for callId=$callId", e)
    }
  }

  private fun onDisconnect(cause: DisconnectCause) {
    Log.i(TAG, "onDisconnect(code=${cause.code}, reason=${cause.reason})")
    disconnected = true
    commandChannel.close()

    when (cause.code) {
      DisconnectCause.REJECTED -> {
        Log.i(TAG, "Call rejected via system UI")
        ActiveCallManager.denyCall()
      }
      DisconnectCause.LOCAL -> {
        Log.i(TAG, "Local hangup via system UI")
        ActiveCallManager.hangup()
      }
      DisconnectCause.REMOTE,
      DisconnectCause.MISSED,
      DisconnectCause.CANCELED -> {
        Log.i(TAG, "Remote/missed/canceled disconnect, no action needed (handled by Signal processors)")
      }
      DisconnectCause.ERROR -> {
        Log.w(TAG, "Disconnect due to error, performing local hangup as fallback")
        ActiveCallManager.hangup()
      }
      else -> {
        Log.w(TAG, "Unknown disconnect cause: ${cause.code}, performing local hangup")
        ActiveCallManager.hangup()
      }
    }
  }

  private fun onSetActive() {
    Log.i(TAG, "onSetActive()")
  }

  private fun onSetInactive() {
    Log.i(TAG, "onSetInactive()")
  }

  sealed interface StartResult {
    data object Ended : StartResult
    data class Failed(val error: Exception, val afterApproval: Boolean) : StartResult
  }
}

@RequiresApi(26)
private fun Int.toAudioDevice(): SignalAudioManager.AudioDevice {
  return when (this) {
    CallEndpointCompat.TYPE_EARPIECE -> SignalAudioManager.AudioDevice.EARPIECE
    CallEndpointCompat.TYPE_SPEAKER -> SignalAudioManager.AudioDevice.SPEAKER_PHONE
    CallEndpointCompat.TYPE_BLUETOOTH -> SignalAudioManager.AudioDevice.BLUETOOTH
    CallEndpointCompat.TYPE_WIRED_HEADSET -> SignalAudioManager.AudioDevice.WIRED_HEADSET
    CallEndpointCompat.TYPE_STREAMING -> SignalAudioManager.AudioDevice.SPEAKER_PHONE
    else -> SignalAudioManager.AudioDevice.EARPIECE
  }
}

private enum class HandleMode(val logValue: String) {
  SIP_OPAQUE("sip-opaque"),
  TEL("tel")
}
