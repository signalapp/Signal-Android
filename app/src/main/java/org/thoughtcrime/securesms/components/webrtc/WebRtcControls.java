package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;

import java.util.Set;

import static java.util.Collections.emptySet;

public final class WebRtcControls {

  public static final WebRtcControls NONE = new WebRtcControls();
  public static final WebRtcControls PIP  = new WebRtcControls(false,
                                                               false,
                                                               false,
                                                               true,
                                                               false,
                                                               CallState.NONE,
                                                               GroupCallState.NONE,
                                                               null,
                                                               FoldableState.flat(),
                                                               SignalAudioManager.AudioDevice.NONE,
                                                               emptySet(),
                                                               false,
                                                               false);

  private final boolean                             isRemoteVideoEnabled;
  private final boolean                             isLocalVideoEnabled;
  private final boolean                             isMoreThanOneCameraAvailable;
  private final boolean                             isInPipMode;
  private final boolean                             hasAtLeastOneRemote;
  private final CallState                           callState;
  private final GroupCallState                      groupCallState;
  private final Long                                participantLimit;
  private final FoldableState                       foldableState;
  private final SignalAudioManager.AudioDevice      activeDevice;
  private final Set<SignalAudioManager.AudioDevice> availableDevices;
  private final boolean                             isCallLink;
  private final boolean                             hasParticipantOverflow;

  private WebRtcControls() {
    this(false,
         false,
         false,
         false,
         false,
         CallState.NONE,
         GroupCallState.NONE,
         null,
         FoldableState.flat(),
         SignalAudioManager.AudioDevice.NONE,
         emptySet(),
         false,
         false);
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public WebRtcControls(boolean isLocalVideoEnabled,
                 boolean isRemoteVideoEnabled,
                 boolean isMoreThanOneCameraAvailable,
                 boolean isInPipMode,
                 boolean hasAtLeastOneRemote,
                 @NonNull CallState callState,
                 @NonNull GroupCallState groupCallState,
                 @Nullable Long participantLimit,
                 @NonNull FoldableState foldableState,
                 @NonNull SignalAudioManager.AudioDevice activeDevice,
                 @NonNull Set<SignalAudioManager.AudioDevice> availableDevices,
                 boolean isCallLink,
                 boolean hasParticipantOverflow)
  {
    this.isLocalVideoEnabled          = isLocalVideoEnabled;
    this.isRemoteVideoEnabled         = isRemoteVideoEnabled;
    this.isMoreThanOneCameraAvailable = isMoreThanOneCameraAvailable;
    this.isInPipMode                  = isInPipMode;
    this.hasAtLeastOneRemote          = hasAtLeastOneRemote;
    this.callState                    = callState;
    this.groupCallState               = groupCallState;
    this.participantLimit             = participantLimit;
    this.foldableState                = foldableState;
    this.activeDevice                 = activeDevice;
    this.availableDevices             = availableDevices;
    this.isCallLink                   = isCallLink;
    this.hasParticipantOverflow       = hasParticipantOverflow;
  }

  public @NonNull WebRtcControls withFoldableState(FoldableState foldableState) {
    return new WebRtcControls(isLocalVideoEnabled,
                              isRemoteVideoEnabled,
                              isMoreThanOneCameraAvailable,
                              isInPipMode,
                              hasAtLeastOneRemote,
                              callState,
                              groupCallState,
                              participantLimit,
                              foldableState,
                              activeDevice,
                              availableDevices,
                              isCallLink,
                              hasParticipantOverflow);
  }

  /**
   * This is only true at the very start of a call and will then never be true again
   */
  public boolean hideControlsSheetInitially() {
    return displayIncomingCallButtons() || callState == CallState.NONE || isHandledElsewhere();
  }

  public boolean displayErrorControls() {
    return isError();
  }

  public boolean displayStartCallControls() {
    return isPreJoin();
  }

  public boolean adjustForFold() {
    return foldableState.isFolded();
  }

  public @Px int getFold() {
    return foldableState.getFoldPoint();
  }

  public @StringRes int getStartCallButtonText() {
    if (isGroupCall()) {
      if (groupCallState == GroupCallState.FULL) {
        return R.string.WebRtcCallView__call_is_full;
      } else if (hasAtLeastOneRemote) {
        return R.string.WebRtcCallView__join_call;
      }
    }
    return R.string.WebRtcCallView__start_call;
  }

  public boolean isStartCallEnabled() {
    return groupCallState != GroupCallState.FULL;
  }

  public boolean displayGroupCallFull() {
    return groupCallState == GroupCallState.FULL;
  }

  public @NonNull String getGroupCallFullMessage(@NonNull Context context) {
    if (participantLimit != null) {
      return context.getString(R.string.WebRtcCallView__the_maximum_number_of_d_participants_has_been_Reached_for_this_call, participantLimit);
    }
    return "";
  }

  public boolean displayGroupMembersButton() {
    return (groupCallState.isAtLeast(GroupCallState.CONNECTING) && hasAtLeastOneRemote) || groupCallState.isAtLeast(GroupCallState.FULL);
  }

  public boolean displayEndCall() {
    return isAtLeastOutgoing() || callState == CallState.RECONNECTING;
  }

  public boolean displayOverflow() {
    return isAtLeastOutgoing() && hasAtLeastOneRemote && isGroupCall() && groupCallState == GroupCallState.CONNECTED;
  }

  public boolean displayMuteAudio() {
    return isPreJoin() || isAtLeastOutgoing();
  }

  public boolean displayVideoToggle() {
    return isPreJoin() || isAtLeastOutgoing();
  }

  public boolean displayAudioToggle() {
    return (isPreJoin() || isAtLeastOutgoing()) && (!isLocalVideoEnabled || isBluetoothHeadsetAvailableForAudioToggle() || isWiredHeadsetAvailableForAudioToggle());
  }

  public boolean displayCameraToggle() {
    return (isPreJoin() || (isAtLeastOutgoing() && !hasAtLeastOneRemote)) && isLocalVideoEnabled && isMoreThanOneCameraAvailable && !isInPipMode;
  }

  public boolean displayRemoteVideoRecycler() {
    return isOngoing() && hasParticipantOverflow;
  }

  public boolean displayAnswerWithoutVideo() {
    return isIncoming() && isRemoteVideoEnabled;
  }

  public boolean displayIncomingCallButtons() {
    return isIncoming();
  }

  public boolean isEarpieceAvailableForAudioToggle() {
    return !isLocalVideoEnabled;
  }

  public boolean isBluetoothHeadsetAvailableForAudioToggle() {
    return availableDevices.contains(SignalAudioManager.AudioDevice.BLUETOOTH);
  }

  public boolean isWiredHeadsetAvailableForAudioToggle() {
    return availableDevices.contains(SignalAudioManager.AudioDevice.WIRED_HEADSET);
  }

  public boolean isFadeOutEnabled() {
    return isAtLeastOutgoing() && isRemoteVideoEnabled && callState != CallState.RECONNECTING;
  }

  public boolean displaySmallCallButtons() {
    return displayedButtonCount() >= 5;
  }

  public boolean displayTopViews() {
    return !isInPipMode;
  }

  public boolean displayReactions() {
    return !isInPipMode;
  }

  public boolean displayRaiseHand() {
    return !isInPipMode;
  }

  public boolean displayWaitingToBeLetIn() {
    return !isInPipMode && groupCallState == GroupCallState.PENDING;
  }

  public @NonNull WebRtcAudioOutput getAudioOutput() {
    switch (activeDevice) {
      case SPEAKER_PHONE:
        return WebRtcAudioOutput.SPEAKER;
      case BLUETOOTH:
        return WebRtcAudioOutput.BLUETOOTH_HEADSET;
      case WIRED_HEADSET:
        return WebRtcAudioOutput.WIRED_HEADSET;
      default:
        return WebRtcAudioOutput.HANDSET;
    }
  }

  public boolean showSmallHeader() {
    return isAtLeastOutgoing();
  }

  public boolean showFullScreenShade() {
    return isPreJoin() || isIncoming();
  }

  public boolean displayRingToggle() {
    return isPreJoin() && isGroupCall() && !isCallLink && !hasAtLeastOneRemote;
  }

  private boolean isError() {
    return callState == CallState.ERROR;
  }

  private boolean isPreJoin() {
    return callState == CallState.PRE_JOIN;
  }

  private boolean isOngoing() {
    return callState == CallState.ONGOING;
  }

  private boolean isIncoming() {
    return callState == CallState.INCOMING;
  }

  private boolean isHandledElsewhere() {
    return callState == CallState.HANDLED_ELSEWHERE;
  }

  private boolean isAtLeastOutgoing() {
    return callState.isAtLeast(CallState.OUTGOING);
  }

  private boolean isGroupCall() {
    return groupCallState != GroupCallState.NONE;
  }

  private int displayedButtonCount() {
    return (displayAudioToggle() ? 1 : 0) +
           (displayVideoToggle() ? 1 : 0) +
           (displayMuteAudio() ? 1 : 0) +
           (displayRingToggle() ? 1 : 0) +
           (displayOverflow() ? 1 : 0) +
           (displayEndCall() ? 1 : 0);
  }

  public enum CallState {
    NONE,
    ERROR,
    HANDLED_ELSEWHERE,
    PRE_JOIN,
    RECONNECTING,
    INCOMING,
    OUTGOING,
    ONGOING,
    ENDING;

    boolean isAtLeast(@SuppressWarnings("SameParameterValue") @NonNull CallState other) {
      return compareTo(other) >= 0;
    }
  }

  public enum GroupCallState {
    NONE,
    DISCONNECTED,
    RECONNECTING,
    CONNECTING,
    FULL,
    PENDING,
    CONNECTED;

    boolean isAtLeast(@SuppressWarnings("SameParameterValue") @NonNull GroupCallState other) {
      return compareTo(other) >= 0;
    }
  }

  public static final class FoldableState {

    private static final int NOT_SET = -1;

    private final int foldPoint;

    public FoldableState(int foldPoint) {
      this.foldPoint = foldPoint;
    }

    public boolean isFolded() {
      return foldPoint != NOT_SET;
    }

    public boolean isFlat() {
      return foldPoint == NOT_SET;
    }

    public int getFoldPoint() {
      return foldPoint;
    }

    public static @NonNull FoldableState folded(int foldPoint) {
      return new FoldableState(foldPoint);
    }

    public static @NonNull FoldableState flat() {
      return new FoldableState(NOT_SET);
    }
  }
}
