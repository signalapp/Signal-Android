#!/usr/bin/env python3
"""Apply the ephemeral proximity-sensor override patch on top of 0001.

Default behavior remains Signal's route-aware behavior:
- handset/earpiece: proximity on
- speaker/wired/Bluetooth: proximity off
The manual override is call-scoped and resets on route changes and hangup.
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


# 1) Add a non-persistent override to the existing wake-lock/proximity manager.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/webrtc/locks/LockManager.java",
    [
        (
            "  private boolean     proximityDisabled = false;\n",
            """  private boolean     proximityDisabled = false;
  private Boolean     proximityOverride = null;
  private PhoneState  currentPhoneState = PhoneState.IDLE;
""",
        ),
        (
            """  public void updatePhoneState(PhoneState state) {
    switch(state) {
      case IDLE:
        setLockState(LockState.SLEEP);
        break;
      case PROCESSING:
        setLockState(LockState.PARTIAL);
        break;
      case INTERACTIVE:
        setLockState(LockState.FULL);
        break;
      case IN_HANDS_FREE_CALL:
        setLockState(LockState.PARTIAL);
        proximityDisabled = true;
        break;
      case IN_VIDEO:
        proximityDisabled = true;
        updateInCallLockState();
        break;
      case IN_CALL:
        proximityDisabled = false;
        updateInCallLockState();
        break;
    }
  }
""",
            """  public synchronized void setProximityOverride(boolean enabled) {
    proximityOverride = enabled;
    applyPhoneState(currentPhoneState);
  }

  public synchronized void clearProximityOverride() {
    proximityOverride = null;
    applyPhoneState(currentPhoneState);
  }

  public synchronized void updatePhoneState(PhoneState state) {
    currentPhoneState = state;
    applyPhoneState(state);
  }

  private void applyPhoneState(PhoneState state) {
    switch(state) {
      case IDLE:
        proximityOverride = null;
        proximityDisabled = false;
        setLockState(LockState.SLEEP);
        break;
      case PROCESSING:
        setLockState(LockState.PARTIAL);
        break;
      case INTERACTIVE:
        setLockState(LockState.FULL);
        break;
      case IN_HANDS_FREE_CALL:
        if (Boolean.TRUE.equals(proximityOverride)) {
          proximityDisabled = false;
          updateInCallLockState();
        } else {
          proximityDisabled = true;
          setLockState(LockState.PARTIAL);
        }
        break;
      case IN_VIDEO:
        proximityDisabled = !Boolean.TRUE.equals(proximityOverride);
        updateInCallLockState();
        break;
      case IN_CALL:
        proximityDisabled = Boolean.FALSE.equals(proximityOverride);
        updateInCallLockState();
        break;
    }
  }
""",
        ),
    ],
)

# 2) Keep video calls route-aware for proximity. Signal normally forces IN_VIDEO
# whenever either camera is active, which disables proximity even if the user
# explicitly selects the phone earpiece. Preserve IN_VIDEO for hands-free video
# routes so the screen stays awake, but use IN_CALL for earpiece video so the
# proximity sensor behaves like a normal phone call.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/service/webrtc/WebRtcUtil.java",
    [
        (
            """  /**
   * Returns the appropriate phone state for an in-call scenario, considering both local and remote video state.
   * If either local or remote video is enabled, returns {@link LockManager.PhoneState#IN_VIDEO} to keep the screen on.
   * Otherwise, falls back to audio-device based phone state.
   */
  public static @NonNull LockManager.PhoneState getInCallPhoneState(@NonNull Context context, boolean localVideoEnabled, boolean remoteVideoEnabled) {
    if (localVideoEnabled || remoteVideoEnabled) {
      return LockManager.PhoneState.IN_VIDEO;
    }
    return getInCallPhoneState(context);
  }
""",
            """  /**
   * Returns the appropriate phone state for an in-call scenario, considering both local and remote video state.
   * During video, keep normal video wake-lock behavior for hands-free routes, but allow
   * the proximity sensor when the user explicitly selects the phone earpiece.
   */
  public static @NonNull LockManager.PhoneState getInCallPhoneState(@NonNull Context context, boolean localVideoEnabled, boolean remoteVideoEnabled) {
    LockManager.PhoneState routeState = getInCallPhoneState(context);
    if (localVideoEnabled || remoteVideoEnabled) {
      return routeState == LockManager.PhoneState.IN_CALL ? LockManager.PhoneState.IN_CALL : LockManager.PhoneState.IN_VIDEO;
    }
    return routeState;
  }
""",
        )
    ],
)

# 3) Forget the override whenever the logical audio route changes and on call shutdown.
# 0001 already added the routeChanged block for HQ Bluetooth; extend that same block.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/service/webrtc/ActiveCallManager.kt",
    [
        (
            """    signalAudioManager?.shutdown()
    signalAudioManager = null

    unregisterNetworkReceiver()
""",
            """    signalAudioManager?.shutdown()
    signalAudioManager = null
    callManager.lockManager.clearProximityOverride()

    unregisterNetworkReceiver()
""",
        ),
        (
            """    if (routeChanged) {
      signalAudioManager?.handleCommand(AudioManagerCommand.SetHighQualityBluetoothAudio(false))
    }
""",
            """    if (routeChanged) {
      signalAudioManager?.handleCommand(AudioManagerCommand.SetHighQualityBluetoothAudio(false))
      callManager.lockManager.clearProximityOverride()
    }
""",
        ),
    ],
)

# 4) UI state is nullable: null means automatic route behavior.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/CallScreenState.kt",
    [
        (
            "  val isLocalScreenSharing: Boolean = false,\n  val highQualityBluetoothAudioEnabled: Boolean = false\n",
            "  val isLocalScreenSharing: Boolean = false,\n  val highQualityBluetoothAudioEnabled: Boolean = false,\n  val proximityOverride: Boolean? = null\n",
        )
    ],
)

# 5) Add a second switch to the same three-dot popup.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/AdditionalActionsPopup.kt",
    [
        (
            "  val isHighQualityBluetoothAudioEnabled: Boolean = false,\n  val isGroupCall: Boolean = true,\n",
            "  val isHighQualityBluetoothAudioEnabled: Boolean = false,\n  val isProximitySensorEnabled: Boolean = false,\n  val isGroupCall: Boolean = true,\n",
        ),
        (
            "  fun onHighQualityBluetoothAudioClick(enabled: Boolean)\n",
            "  fun onHighQualityBluetoothAudioClick(enabled: Boolean)\n  fun onProximitySensorClick(enabled: Boolean)\n",
        ),
        (
            "    override fun onHighQualityBluetoothAudioClick(enabled: Boolean) = Unit\n",
            "    override fun onHighQualityBluetoothAudioClick(enabled: Boolean) = Unit\n    override fun onProximitySensorClick(enabled: Boolean) = Unit\n",
        ),
        (
            """      displayHighQualityBluetoothToggle = state.displayHighQualityBluetoothToggle,
      isHighQualityBluetoothAudioEnabled = state.isHighQualityBluetoothAudioEnabled,
      onHighQualityBluetoothAudioClick = state.listener::onHighQualityBluetoothAudioClick
""",
            """      displayHighQualityBluetoothToggle = state.displayHighQualityBluetoothToggle,
      isHighQualityBluetoothAudioEnabled = state.isHighQualityBluetoothAudioEnabled,
      onHighQualityBluetoothAudioClick = state.listener::onHighQualityBluetoothAudioClick,
      isProximitySensorEnabled = state.isProximitySensorEnabled,
      onProximitySensorClick = state.listener::onProximitySensorClick
""",
        ),
        (
            """  displayHighQualityBluetoothToggle: Boolean = false,
  isHighQualityBluetoothAudioEnabled: Boolean = false,
  onHighQualityBluetoothAudioClick: (Boolean) -> Unit = {}
""",
            """  displayHighQualityBluetoothToggle: Boolean = false,
  isHighQualityBluetoothAudioEnabled: Boolean = false,
  onHighQualityBluetoothAudioClick: (Boolean) -> Unit = {},
  isProximitySensorEnabled: Boolean = false,
  onProximitySensorClick: (Boolean) -> Unit = {}
""",
        ),
        (
            """    if (displayHighQualityBluetoothToggle) {
      CallScreenMenuToggle(
        title = stringResource(R.string.CallOverflowPopupWindow__high_quality_bluetooth_audio),
        checked = isHighQualityBluetoothAudioEnabled,
        onCheckedChange = onHighQualityBluetoothAudioClick
      )
    }
""",
            """    if (displayHighQualityBluetoothToggle) {
      CallScreenMenuToggle(
        title = stringResource(R.string.CallOverflowPopupWindow__high_quality_bluetooth_audio),
        checked = isHighQualityBluetoothAudioEnabled,
        onCheckedChange = onHighQualityBluetoothAudioClick
      )
    }
    CallScreenMenuToggle(
      title = stringResource(R.string.CallOverflowPopupWindow__proximity_sensor),
      checked = isProximitySensorEnabled,
      onCheckedChange = onProximitySensorClick
    )
""",
        ),
    ],
)

# 6) Derive automatic proximity state from the selected route and use manual
# override only until the route changes.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/CallScreen.kt",
    [
        (
            """    callScreenState.highQualityBluetoothAudioEnabled,
    callControlsState.displayEndCallButton,
""",
            """    callScreenState.highQualityBluetoothAudioEnabled,
    callScreenState.proximityOverride,
    callControlsState.displayEndCallButton,
""",
        ),
        (
            """      isHighQualityBluetoothAudioEnabled = callScreenState.highQualityBluetoothAudioEnabled,
      isGroupCall = callControlsState.isGroupCall,
""",
            """      isHighQualityBluetoothAudioEnabled = callScreenState.highQualityBluetoothAudioEnabled,
      isProximitySensorEnabled = callScreenState.proximityOverride ?: (callControlsState.audioOutput == WebRtcAudioOutput.HANDSET),
      isGroupCall = callControlsState.isGroupCall,
""",
        ),
    ],
)

# 7) Hook the switch to LockManager; extend the route UI reset from patch 0001.
edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/v2/ComposeCallScreenMediator.kt",
    [
        (
            """      LaunchedEffect(callControlsState.audioOutput) {
        callScreenViewModel.callScreenState.update { it.copy(highQualityBluetoothAudioEnabled = false) }
      }
""",
            """      LaunchedEffect(callControlsState.audioOutput) {
        callScreenViewModel.callScreenState.update {
          it.copy(
            highQualityBluetoothAudioEnabled = false,
            proximityOverride = null
          )
        }
      }
""",
        ),
        (
            """  override fun onScreenShareClick(sharing: Boolean) {
""",
            """  override fun onProximitySensorClick(enabled: Boolean) {
    AppDependencies.signalCallManager.lockManager.setProximityOverride(enabled)
    callScreenViewModel.callScreenState.update { it.copy(proximityOverride = enabled) }
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
            "    <string name=\"CallOverflowPopupWindow__high_quality_bluetooth_audio\">High quality Bluetooth audio</string>\n",
            """    <string name="CallOverflowPopupWindow__high_quality_bluetooth_audio">High quality Bluetooth audio</string>
    <!-- A temporary call switch overriding the route-derived proximity sensor behavior. -->
    <string name="CallOverflowPopupWindow__proximity_sensor">Proximity sensor</string>
""",
        )
    ],
)

print("Proximity override patch applied successfully")
