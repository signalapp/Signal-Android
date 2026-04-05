package org.thoughtcrime.securesms.service.webrtc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.AudioOutputOption
import org.thoughtcrime.securesms.components.webrtc.v2.CallIntent
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager

sealed class TelecomCommand {
  object Activate : TelecomCommand()
  data class Disconnect(val cause: Int) : TelecomCommand()
  data class ChangeEndpoint(val device: SignalAudioManager.AudioDevice) : TelecomCommand()
}

@RequiresApi(26)
class TelecomCallController(
  private val context: Context,
  private val recipientId: RecipientId,
  private val callId: Long,
  private val isVideoCall: Boolean,
  private val isOutgoing: Boolean,
  private val callsManager: CallsManager
) {

  companion object {
    private val TAG: String = Log.tag(TelecomCallController::class.java)
  }

  private val commandChannel = Channel<TelecomCommand>(Channel.BUFFERED)

  @Volatile
  var currentAudioDevice: SignalAudioManager.AudioDevice = SignalAudioManager.AudioDevice.NONE
    private set

  @Volatile
  private var cachedEndpoints: List<CallEndpointCompat> = emptyList()

  @Volatile
  private var disconnected: Boolean = false

  fun getAvailableAudioOutputOptions(): List<AudioOutputOption> {
    return cachedEndpoints
      .map { AudioOutputOption(it.name.toString(), it.type.toAudioDevice(), it.type) }
      .distinctBy { it.deviceType }
      .filterNot { it.deviceType == SignalAudioManager.AudioDevice.NONE }
  }

  fun getCurrentEndpointDeviceId(): Int {
    return cachedEndpoints.firstOrNull { it.type.toAudioDevice() == currentAudioDevice }?.type ?: -1
  }

  fun activate() {
    Log.i(TAG, "activate() recipientId=$recipientId callId=$callId")
    commandChannel.trySend(TelecomCommand.Activate)
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
    commandChannel.trySend(TelecomCommand.ChangeEndpoint(device))
  }

  suspend fun start() {
    val recipient = Recipient.resolved(recipientId)
    val displayName = if (SignalStore.settings.messageNotificationsPrivacy.isDisplayContact) recipient.getDisplayName(context) else context.getString(R.string.Recipient_signal_call)
    val address = Uri.fromParts("sip", recipientId.serialize(), null)

    val direction = if (isOutgoing) CallAttributesCompat.DIRECTION_OUTGOING else CallAttributesCompat.DIRECTION_INCOMING
    val callType = if (isVideoCall) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL

    val attributes = CallAttributesCompat(
      displayName = displayName,
      address = address,
      direction = direction,
      callType = callType
    )

    Log.i(TAG, "start() recipientId=$recipientId callId=$callId isOutgoing=$isOutgoing isVideo=$isVideoCall")

    callsManager.addCall(
      callAttributes = attributes,
      onAnswer = { callType -> onAnswer(callType) },
      onDisconnect = { cause -> onDisconnect(cause) },
      onSetActive = { onSetActive() },
      onSetInactive = { onSetInactive() }
    ) {
      Log.i(TAG, "addCall block entered, callControlScope active for callId=$callId")

      if (isOutgoing) {
        Log.i(TAG, "Posting outgoing call notification immediately for callId=$callId")
        try {
          val notification = CallNotificationBuilder.getCallInProgressNotification(
            context,
            CallNotificationBuilder.TYPE_OUTGOING_RINGING,
            recipient,
            isVideoCall,
            true
          )
          NotificationManagerCompat.from(context).notify(CallNotificationBuilder.WEBRTC_NOTIFICATION, notification)
        } catch (e: SecurityException) {
          Log.w(TAG, "Failed to post outgoing call notification", e)
        }
      }

      AppDependencies.signalCallManager.setTelecomApproved(callId, recipientId)
      Log.i(TAG, "setTelecomApproved fired for callId=$callId recipientId=$recipientId")

      var needToResetAudioRoute = isOutgoing && !isVideoCall
      var initialEndpoint: SignalAudioManager.AudioDevice? = null

      launch {
        currentCallEndpoint.collect { endpoint ->
          val activeDevice = endpoint.type.toAudioDevice()
          Log.i(TAG, "currentCallEndpoint changed: ${endpoint.name} (type=${endpoint.type}) -> $activeDevice")
          currentAudioDevice = activeDevice

          val available = cachedEndpoints.map { it.type.toAudioDevice() }.toSet()
          AppDependencies.signalCallManager.onAudioDeviceChanged(activeDevice, available)

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

      launch {
        isMuted.collect { muted ->
          Log.i(TAG, "isMuted changed: $muted for callId=$callId")
          AppDependencies.signalCallManager.setMuteAudio(muted)
        }
      }

      launch {
        availableEndpoints.collect { endpoints ->
          cachedEndpoints = endpoints
          val available = endpoints.map { it.type.toAudioDevice() }.toSet()
          Log.i(TAG, "availableEndpoints changed: $available (${endpoints.size} endpoints)")
          AppDependencies.signalCallManager.onAudioDeviceChanged(currentAudioDevice, available)
        }
      }

      launch {
        for (command in commandChannel) {
          when (command) {
            is TelecomCommand.Activate -> {
              val result = setActive()
              Log.i(TAG, "setActive result: $result")
              needToResetAudioRoute = false
            }
            is TelecomCommand.Disconnect -> {
              val result = disconnect(DisconnectCause(command.cause))
              Log.i(TAG, "disconnect result: $result")
              break
            }
            is TelecomCommand.ChangeEndpoint -> {
              val targetDevice = command.device
              val target = cachedEndpoints.firstOrNull { it.type.toAudioDevice() == targetDevice }
              if (target != null) {
                val result = requestEndpointChange(target)
                Log.i(TAG, "requestEndpointChange($targetDevice) result: $result")
              } else {
                Log.w(TAG, "No endpoint found for device: $targetDevice, available: ${cachedEndpoints.map { it.type.toAudioDevice() }}")
              }
            }
          }
        }
      }
    }
  }

  private fun onAnswer(callType: Int) {
    val hasRecordAudio = Permissions.hasAll(context, android.Manifest.permission.RECORD_AUDIO)
    Log.i(TAG, "onAnswer(callType=$callType) recipientId=$recipientId hasRecordAudio=$hasRecordAudio")
    if (hasRecordAudio) {
      AppDependencies.signalCallManager.acceptCall(false)
    } else {
      Log.i(TAG, "Missing RECORD_AUDIO permission, launching CallIntent activity")
      val intent = CallIntent.Builder(context)
        .withAddedIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .withAction(if (isVideoCall) CallIntent.Action.ANSWER_VIDEO else CallIntent.Action.ANSWER_AUDIO)
        .build()
      context.startActivity(intent)
    }
  }

  private fun onDisconnect(cause: DisconnectCause) {
    Log.i(TAG, "onDisconnect(code=${cause.code}, reason=${cause.reason})")
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
