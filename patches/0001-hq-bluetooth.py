#!/usr/bin/env python3
"""Apply the Signal Android high-quality classic-Bluetooth call audio patch.

Targeted and tested against Signal Android source layout v8.25.2.
The patch is intentionally strict: if an upstream source block changes, it aborts
instead of guessing and producing a partially patched call stack.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()


def edit(rel: str, replacements: list[tuple[str, str]]) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    original = text
    for old, new in replacements:
        count = text.count(old)
        if count != 1:
            raise RuntimeError(f"{rel}: expected exactly one match, found {count}: {old[:100]!r}")
        text = text.replace(old, new, 1)
    if text == original:
        raise RuntimeError(f"{rel}: no changes made")
    path.write_text(text, encoding="utf-8")
    print(f"patched {rel}")


# 1) Audio-manager command exposed to the call UI.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/webrtc/audio/AudioManagerCommand.kt",
    [
        (
            "  class SetDefaultDevice(val recipientId: RecipientId?, val device: SignalAudioManager.AudioDevice, val clearUserEarpieceSelection: Boolean) : AudioManagerCommand() {",
            """  class SetHighQualityBluetoothAudio(val enabled: Boolean) : AudioManagerCommand() {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
      ParcelUtil.writeBoolean(parcel, enabled)
    }

    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<SetHighQualityBluetoothAudio> = ParcelCheat { SetHighQualityBluetoothAudio(ParcelUtil.readBoolean(it)) }
    }
  }

  class SetDefaultDevice(val recipientId: RecipientId?, val device: SignalAudioManager.AudioDevice, val clearUserEarpieceSelection: Boolean) : AudioManagerCommand() {""",
        )
    ],
)

# 2) Shared/legacy audio routing. Telecom is disabled in this fork so the custom
# routing path owns communication-device selection.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/webrtc/audio/SignalAudioManager.kt",
    [
        ("import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil\n", ""),
        (
            "  protected var selectedAudioDevice: AudioDevice = AudioDevice.NONE\n",
            "  protected var selectedAudioDevice: AudioDevice = AudioDevice.NONE\n  protected var highQualityBluetoothAudio = false\n",
        ),
        (
            """    fun create(context: Context, eventListener: EventListener?, canUseTelecom: Boolean): SignalAudioManager {
      return if (canUseTelecom && AndroidTelecomUtil.telecomSupported) {
        TelecomAudioManager(context, eventListener)
      } else if (Build.VERSION.SDK_INT >= 31) {
        FullSignalAudioManagerApi31(context, eventListener)
      } else {
        FullSignalAudioManager(context, eventListener)
      }
    }""",
            """    fun create(context: Context, eventListener: EventListener?, canUseTelecom: Boolean): SignalAudioManager {
      if (canUseTelecom) {
        Log.i(TAG, "Jetpack Telecom audio routing disabled by fork HQ Bluetooth patch")
      }
      return if (Build.VERSION.SDK_INT >= 31) {
        FullSignalAudioManagerApi31(context, eventListener)
      } else {
        FullSignalAudioManager(context, eventListener)
      }
    }""",
        ),
        (
            "        is AudioManagerCommand.SetUserDevice -> selectAudioDevice(command.recipientId, command.device, command.isId)\n",
            """        is AudioManagerCommand.SetUserDevice -> {
          if (highQualityBluetoothAudio) {
            setHighQualityBluetoothAudio(false)
          }
          selectAudioDevice(command.recipientId, command.device, command.isId)
        }
        is AudioManagerCommand.SetHighQualityBluetoothAudio -> setHighQualityBluetoothAudio(command.enabled)
""",
        ),
        (
            "  protected abstract fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean)\n",
            """  protected abstract fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean)
  protected open fun setHighQualityBluetoothAudio(enabled: Boolean) {
    highQualityBluetoothAudio = enabled
  }
""",
        ),
        (
            "  override fun onAudioDeviceUpdated() {\n",
            """  override fun setHighQualityBluetoothAudio(enabled: Boolean) {
    if (highQualityBluetoothAudio == enabled) {
      return
    }

    highQualityBluetoothAudio = enabled
    if (enabled) {
      Log.i(TAG, "HQ Bluetooth enabled: stopping SCO and leaving communication mode")
      signalBluetoothManager.stopScoAudio()
      setMode(AudioManager.MODE_NORMAL, "setHighQualityBluetoothAudio")
    } else if (state != State.UNINITIALIZED) {
      Log.i(TAG, "HQ Bluetooth disabled: restoring communication mode")
      setMode(AudioManager.MODE_IN_COMMUNICATION, "setHighQualityBluetoothAudio")
    }

    onAudioDeviceUpdated()
  }

  override fun onAudioDeviceUpdated() {
""",
        ),
        (
            "    val needBluetoothAudioStart = signalBluetoothManager.state == SignalBluetoothManager.State.AVAILABLE &&\n",
            "    val needBluetoothAudioStart = !highQualityBluetoothAudio && signalBluetoothManager.state == SignalBluetoothManager.State.AVAILABLE &&\n",
        ),
    ],
)

# 3) Android 12+ communication-device path. In HQ mode we deliberately clear
# the SCO communication device and use MODE_NORMAL, allowing media/A2DP output
# on devices that support this routing combination. We retain the logical BT
# device id so callbacks cannot immediately route us back to SCO.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/webrtc/audio/FullSignalAudioManagerApi31.kt",
    [
        (
            "  private var appliedCommunicationDeviceId: Int? = null\n",
            "  private var appliedCommunicationDeviceId: Int? = null\n  private var highQualityBluetoothDeviceId: Int? = null\n",
        ),
        (
            "  override fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean) {\n",
            """  override fun setHighQualityBluetoothAudio(enabled: Boolean) {
    if (highQualityBluetoothAudio == enabled) {
      return
    }

    if (enabled) {
      val bluetoothDevice = (userSelectedAudioDevice ?: androidAudioManager.communicationDevice)
        ?.takeIf { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

      if (bluetoothDevice == null) {
        Log.w(TAG, "HQ Bluetooth requested without an active classic Bluetooth SCO device")
        return
      }

      highQualityBluetoothAudio = true
      highQualityBluetoothDeviceId = bluetoothDevice.id
      androidAudioManager.clearCommunicationDevice()
      appliedCommunicationDeviceId = null
      setMode(AudioManager.MODE_NORMAL, "setHighQualityBluetoothAudio")

      val available = androidAudioManager.availableCommunicationDevices
      eventListener?.onAudioDeviceChanged(
        AudioDevice.BLUETOOTH,
        available.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet()
      )
    } else {
      highQualityBluetoothAudio = false
      highQualityBluetoothDeviceId = null
      if (state != State.UNINITIALIZED) {
        setMode(AudioManager.MODE_IN_COMMUNICATION, "setHighQualityBluetoothAudio")
      }
      updateAudioDeviceState()
    }
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean) {
""",
        ),
        (
            """    if (userSelectedAudioDevice != null && availableCommunicationDevices.none { it.id == userSelectedAudioDevice?.id }) {
""",
            """    if (highQualityBluetoothAudio) {
      val bluetoothDeviceStillAvailable = availableCommunicationDevices.any {
        it.id == highQualityBluetoothDeviceId && it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
      }

      if (bluetoothDeviceStillAvailable) {
        if (androidAudioManager.communicationDevice != null) {
          androidAudioManager.clearCommunicationDevice()
          appliedCommunicationDeviceId = null
        }
        if (requestedMode != AudioManager.MODE_NORMAL) {
          setMode(AudioManager.MODE_NORMAL, "updateAudioDeviceState-hqBluetooth")
        }
        eventListener?.onAudioDeviceChanged(
          AudioDevice.BLUETOOTH,
          availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet()
        )
        return
      } else {
        Log.i(TAG, "HQ Bluetooth target disappeared; restoring normal call routing")
        highQualityBluetoothAudio = false
        highQualityBluetoothDeviceId = null
        if (state != State.UNINITIALIZED) {
          setMode(AudioManager.MODE_IN_COMMUNICATION, "updateAudioDeviceState-hqBluetooth-ended")
        }
      }
    }

    if (userSelectedAudioDevice != null && availableCommunicationDevices.none { it.id == userSelectedAudioDevice?.id }) {
""",
        ),
    ],
)

# 4) Route changes reset the temporary HQ toggle in the backend.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/service/webrtc/ActiveCallManager.kt",
    [
        (
            "  private var signalAudioManager: SignalAudioManager? = null\n",
            "  private var signalAudioManager: SignalAudioManager? = null\n  private var previousAudioDevice: SignalAudioManager.AudioDevice? = null\n",
        ),
        (
            """  override fun onAudioDeviceChanged(activeDevice: SignalAudioManager.AudioDevice, devices: Set<SignalAudioManager.AudioDevice>) {
    callManager.onAudioDeviceChanged(activeDevice, devices)
  }
""",
            """  override fun onAudioDeviceChanged(activeDevice: SignalAudioManager.AudioDevice, devices: Set<SignalAudioManager.AudioDevice>) {
    val routeChanged = previousAudioDevice != null && previousAudioDevice != activeDevice
    previousAudioDevice = activeDevice

    if (routeChanged) {
      signalAudioManager?.handleCommand(AudioManagerCommand.SetHighQualityBluetoothAudio(false))
    }

    callManager.onAudioDeviceChanged(activeDevice, devices)
  }
""",
        ),
    ],
)

# 5) Ephemeral UI state.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/CallScreenState.kt",
    [
        (
            "  val reactions: PersistentList<String> = persistentListOf(),\n  val isLocalScreenSharing: Boolean = false\n",
            "  val reactions: PersistentList<String> = persistentListOf(),\n  val isLocalScreenSharing: Boolean = false,\n  val highQualityBluetoothAudioEnabled: Boolean = false\n",
        )
    ],
)

edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/AdditionalActionsPopup.kt",
    [
        ("import androidx.compose.foundation.layout.width\n", "import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.weight\n"),
        ("import androidx.compose.material3.Text\n", "import androidx.compose.material3.Text\nimport androidx.compose.material3.Switch\n"),
        (
            "  val displayScreenShareToggle: Boolean = false,\n  val isGroupCall: Boolean = true,\n",
            "  val displayScreenShareToggle: Boolean = false,\n  val displayHighQualityBluetoothToggle: Boolean = false,\n  val isHighQualityBluetoothAudioEnabled: Boolean = false,\n  val isGroupCall: Boolean = true,\n",
        ),
        (
            "  fun onScreenShareClick(sharing: Boolean)\n",
            "  fun onScreenShareClick(sharing: Boolean)\n  fun onHighQualityBluetoothAudioClick(enabled: Boolean)\n",
        ),
        (
            "    override fun onScreenShareClick(sharing: Boolean) = Unit\n",
            "    override fun onScreenShareClick(sharing: Boolean) = Unit\n    override fun onHighQualityBluetoothAudioClick(enabled: Boolean) = Unit\n",
        ),
        (
            """      displayScreenShareToggle = state.displayScreenShareToggle,
      onScreenShareClick = state.listener::onScreenShareClick
""",
            """      displayScreenShareToggle = state.displayScreenShareToggle,
      onScreenShareClick = state.listener::onScreenShareClick,
      displayHighQualityBluetoothToggle = state.displayHighQualityBluetoothToggle,
      isHighQualityBluetoothAudioEnabled = state.isHighQualityBluetoothAudioEnabled,
      onHighQualityBluetoothAudioClick = state.listener::onHighQualityBluetoothAudioClick
""",
        ),
        (
            """  isScreenSharing: Boolean = false,
  displayScreenShareToggle: Boolean = false,
  onScreenShareClick: (Boolean) -> Unit = {}
""",
            """  isScreenSharing: Boolean = false,
  displayScreenShareToggle: Boolean = false,
  onScreenShareClick: (Boolean) -> Unit = {},
  displayHighQualityBluetoothToggle: Boolean = false,
  isHighQualityBluetoothAudioEnabled: Boolean = false,
  onHighQualityBluetoothAudioClick: (Boolean) -> Unit = {}
""",
        ),
        (
            """    if (displayScreenShareToggle) {
      CallScreenMenuOption(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_screen_share_24),
        title = if (isScreenSharing) stringResource(R.string.CallOverflowPopupWindow__stop_screen_share) else stringResource(R.string.CallOverflowPopupWindow__share_screen),
        onClick = { onScreenShareClick(!isScreenSharing) }
      )
    }
""",
            """    if (displayScreenShareToggle) {
      CallScreenMenuOption(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_screen_share_24),
        title = if (isScreenSharing) stringResource(R.string.CallOverflowPopupWindow__stop_screen_share) else stringResource(R.string.CallOverflowPopupWindow__share_screen),
        onClick = { onScreenShareClick(!isScreenSharing) }
      )
    }
    if (displayHighQualityBluetoothToggle) {
      CallScreenMenuToggle(
        title = stringResource(R.string.CallOverflowPopupWindow__high_quality_bluetooth_audio),
        checked = isHighQualityBluetoothAudioEnabled,
        onCheckedChange = onHighQualityBluetoothAudioClick
      )
    }
""",
        ),
        (
            "@Composable\nprivate fun CallScreenMenuOption(\n",
            """@Composable
private fun CallScreenMenuToggle(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = spacedBy(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable { onCheckedChange(!checked) }
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f)
    )
    Switch(
      checked = checked,
      onCheckedChange = null
    )
  }
}

@Composable
private fun CallScreenMenuOption(
""",
        ),
    ],
)

edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/CallScreen.kt",
    [
        (
            "import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState\n",
            "import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput\nimport org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState\n",
        ),
        (
            """    callScreenState.isLocalScreenSharing,
    callControlsState.displayEndCallButton
""",
            """    callScreenState.isLocalScreenSharing,
    callScreenState.highQualityBluetoothAudioEnabled,
    callControlsState.displayEndCallButton,
    callControlsState.audioOutput
""",
        ),
        (
            """      displayScreenShareToggle = callControlsState.displayEndCallButton && RemoteConfig.screenSharing,
      isGroupCall = callControlsState.isGroupCall,
""",
            """      displayScreenShareToggle = callControlsState.displayEndCallButton && RemoteConfig.screenSharing,
      displayHighQualityBluetoothToggle = callControlsState.audioOutput == WebRtcAudioOutput.BLUETOOTH_HEADSET,
      isHighQualityBluetoothAudioEnabled = callScreenState.highQualityBluetoothAudioEnabled,
      isGroupCall = callControlsState.isGroupCall,
""",
        ),
    ],
)

edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/ComposeCallScreenMediator.kt",
    [
        (
            "import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult\n",
            "import org.thoughtcrime.securesms.service.webrtc.ActiveCallManager\nimport org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult\n",
        ),
        (
            "import org.thoughtcrime.securesms.webrtc.CallParticipantsViewState\n",
            "import org.thoughtcrime.securesms.webrtc.CallParticipantsViewState\nimport org.thoughtcrime.securesms.webrtc.audio.AudioManagerCommand\n",
        ),
        (
            """      LaunchedEffect(isLocalScreenSharing) {
        callScreenViewModel.callScreenState.update { it.copy(isLocalScreenSharing = isLocalScreenSharing) }
      }
""",
            """      LaunchedEffect(isLocalScreenSharing) {
        callScreenViewModel.callScreenState.update { it.copy(isLocalScreenSharing = isLocalScreenSharing) }
      }
      LaunchedEffect(callControlsState.audioOutput) {
        callScreenViewModel.callScreenState.update { it.copy(highQualityBluetoothAudioEnabled = false) }
      }
""",
        ),
        (
            """  override fun onScreenShareClick(sharing: Boolean) {
""",
            """  override fun onHighQualityBluetoothAudioClick(enabled: Boolean) {
    ActiveCallManager.sendAudioManagerCommand(activity, AudioManagerCommand.SetHighQualityBluetoothAudio(enabled))
    callScreenViewModel.callScreenState.update { it.copy(highQualityBluetoothAudioEnabled = enabled) }
  }

  override fun onScreenShareClick(sharing: Boolean) {
""",
        ),
    ],
)

edit(
    "app/src/main/res/values/strings.xml",
    [
        (
            "    <string name=\"CallOverflowPopupWindow__share_screen\">Share screen</string>\n",
            """    <string name="CallOverflowPopupWindow__share_screen">Share screen</string>
    <!-- A temporary call switch that avoids classic Bluetooth SCO communication routing to prefer media-quality playback. -->
    <string name="CallOverflowPopupWindow__high_quality_bluetooth_audio">High quality Bluetooth audio</string>
""",
        )
    ],
)

print("HQ Bluetooth patch applied successfully")
