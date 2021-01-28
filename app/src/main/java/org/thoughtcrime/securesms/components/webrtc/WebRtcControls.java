package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public final class WebRtcControls {

  public static final WebRtcControls NONE = new WebRtcControls();
  public static final WebRtcControls PIP  = new WebRtcControls(false, false, false, false, true, false, CallState.NONE, GroupCallState.NONE, WebRtcAudioOutput.HANDSET, null);

  private final boolean           isRemoteVideoEnabled;
  private final boolean           isLocalVideoEnabled;
  private final boolean           isMoreThanOneCameraAvailable;
  private final boolean           isBluetoothAvailable;
  private final boolean           isInPipMode;
  private final boolean           hasAtLeastOneRemote;
  private final CallState         callState;
  private final GroupCallState    groupCallState;
  private final WebRtcAudioOutput audioOutput;
  private final Long              participantLimit;

  private WebRtcControls() {
    this(false, false, false, false, false, false, CallState.NONE, GroupCallState.NONE, WebRtcAudioOutput.HANDSET, null);
  }

  WebRtcControls(boolean isLocalVideoEnabled,
                 boolean isRemoteVideoEnabled,
                 boolean isMoreThanOneCameraAvailable,
                 boolean isBluetoothAvailable,
                 boolean isInPipMode,
                 boolean hasAtLeastOneRemote,
                 @NonNull CallState callState,
                 @NonNull GroupCallState groupCallState,
                 @NonNull WebRtcAudioOutput audioOutput,
                 @Nullable Long participantLimit)
  {
    this.isLocalVideoEnabled          = isLocalVideoEnabled;
    this.isRemoteVideoEnabled         = isRemoteVideoEnabled;
    this.isBluetoothAvailable         = isBluetoothAvailable;
    this.isMoreThanOneCameraAvailable = isMoreThanOneCameraAvailable;
    this.isInPipMode                  = isInPipMode;
    this.hasAtLeastOneRemote          = hasAtLeastOneRemote;
    this.callState                    = callState;
    this.groupCallState               = groupCallState;
    this.audioOutput                  = audioOutput;
    this.participantLimit             = participantLimit;
  }

  boolean displayErrorControls() {
    return isError();
  }

  boolean displayStartCallControls() {
    return isPreJoin();
  }

  @StringRes int getStartCallButtonText() {
    if (isGroupCall()) {
      if (groupCallState == GroupCallState.FULL) {
        return R.string.WebRtcCallView__call_is_full;
      } else if (hasAtLeastOneRemote) {
        return R.string.WebRtcCallView__join_call;
      }
    }
    return R.string.WebRtcCallView__start_call;
  }

  boolean isStartCallEnabled() {
    return groupCallState != GroupCallState.FULL;
  }

  boolean displayGroupCallFull() {
    return groupCallState == GroupCallState.FULL;
  }

  @NonNull String getGroupCallFullMessage(@NonNull Context context) {
    if (participantLimit != null) {
      return context.getString(R.string.WebRtcCallView__the_maximum_number_of_d_participants_has_been_Reached_for_this_call, participantLimit);
    }
    return "";
  }

  boolean displayGroupMembersButton() {
    return groupCallState.isAtLeast(GroupCallState.CONNECTING);
  }

  boolean displayEndCall() {
    return isAtLeastOutgoing();
  }

  boolean displayMuteAudio() {
    return isPreJoin() || isAtLeastOutgoing();
  }

  boolean displayVideoToggle() {
    return isPreJoin() || isAtLeastOutgoing();
  }

  boolean displayAudioToggle() {
    return (isPreJoin() || isAtLeastOutgoing()) && (!isLocalVideoEnabled || isBluetoothAvailable);
  }

  boolean displayCameraToggle() {
    return (isPreJoin() || isAtLeastOutgoing()) && isLocalVideoEnabled && isMoreThanOneCameraAvailable;
  }

  boolean displayRemoteVideoRecycler() {
    return isOngoing();
  }

  boolean displayAnswerWithAudio() {
    return isIncoming() && isRemoteVideoEnabled;
  }

  boolean displayIncomingCallButtons() {
    return isIncoming();
  }

  boolean enableHandsetInAudioToggle() {
    return !isLocalVideoEnabled;
  }

  boolean enableHeadsetInAudioToggle() {
    return isBluetoothAvailable;
  }

  boolean isFadeOutEnabled() {
    return isAtLeastOutgoing() && isRemoteVideoEnabled;
  }

  boolean displaySmallOngoingCallButtons() {
    return isAtLeastOutgoing() && displayAudioToggle() && displayCameraToggle();
  }

  boolean displayLargeOngoingCallButtons() {
    return isAtLeastOutgoing() && !(displayAudioToggle() && displayCameraToggle());
  }

  boolean displayTopViews() {
    return !isInPipMode;
  }

  @NonNull WebRtcAudioOutput getAudioOutput() {
    return audioOutput;
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

  private boolean isAtLeastOutgoing() {
    return callState.isAtLeast(CallState.OUTGOING);
  }

  private boolean isGroupCall() {
    return groupCallState != GroupCallState.NONE;
  }

  public enum CallState {
    NONE,
    ERROR,
    PRE_JOIN,
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
    CONNECTED;

    boolean isAtLeast(@SuppressWarnings("SameParameterValue") @NonNull GroupCallState other) {
      return compareTo(other) >= 0;
    }
  }
}
