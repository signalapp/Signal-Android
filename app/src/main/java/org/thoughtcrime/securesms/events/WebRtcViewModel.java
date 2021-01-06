package org.thoughtcrime.securesms.events;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.OptionalLong;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;

import java.util.List;
import java.util.Set;

public class WebRtcViewModel {

  public enum State {
    IDLE,

    // Normal states
    CALL_PRE_JOIN,
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_CONNECTED,
    CALL_RINGING,
    CALL_BUSY,
    CALL_DISCONNECTED,
    CALL_NEEDS_PERMISSION,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY,

    // Multiring Hangup States
    CALL_ACCEPTED_ELSEWHERE,
    CALL_DECLINED_ELSEWHERE,
    CALL_ONGOING_ELSEWHERE;

    public boolean isErrorState() {
      return this == NETWORK_FAILURE       ||
             this == RECIPIENT_UNAVAILABLE ||
             this == NO_SUCH_USER          ||
             this == UNTRUSTED_IDENTITY;
    }

    public boolean isPreJoinOrNetworkUnavailable() {
      return this == CALL_PRE_JOIN || this == NETWORK_FAILURE;
    }

    public boolean isPassedPreJoin() {
      return this.ordinal() > CALL_PRE_JOIN.ordinal();
    }
  }

  public enum GroupCallState {
    IDLE,
    DISCONNECTED,
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    CONNECTED_AND_JOINING,
    CONNECTED_AND_JOINED;

    public boolean isNotIdle() {
      return this != IDLE;
    }

    public boolean isConnected() {
      switch (this) {
        case CONNECTED:
        case CONNECTED_AND_JOINING:
        case CONNECTED_AND_JOINED:
          return true;
      }

      return false;
    }

    public boolean isNotIdleOrConnected() {
      switch (this) {
        case DISCONNECTED:
        case CONNECTING:
        case RECONNECTING:
          return true;
      }

      return false;
    }
  }

  private final @NonNull State          state;
  private final @NonNull GroupCallState groupState;
  private final @NonNull Recipient      recipient;

  private final boolean isBluetoothAvailable;
  private final boolean isRemoteVideoOffer;
  private final long    callConnectedTime;

  private final CallParticipant       localParticipant;
  private final List<CallParticipant> remoteParticipants;
  private final Set<RecipientId>      identityChangedRecipients;
  private final OptionalLong          remoteDevicesCount;
  private final Long                  participantLimit;

  public WebRtcViewModel(@NonNull WebRtcServiceState state) {
    this.state                     = state.getCallInfoState().getCallState();
    this.groupState                = state.getCallInfoState().getGroupCallState();
    this.recipient                 = state.getCallInfoState().getCallRecipient();
    this.isRemoteVideoOffer        = state.getCallSetupState().isRemoteVideoOffer();
    this.isBluetoothAvailable      = state.getLocalDeviceState().isBluetoothAvailable();
    this.remoteParticipants        = state.getCallInfoState().getRemoteCallParticipants();
    this.identityChangedRecipients = state.getCallInfoState().getIdentityChangedRecipients();
    this.callConnectedTime         = state.getCallInfoState().getCallConnectedTime();
    this.remoteDevicesCount        = state.getCallInfoState().getRemoteDevicesCount();
    this.participantLimit          = state.getCallInfoState().getParticipantLimit();
    this.localParticipant          = CallParticipant.createLocal(state.getLocalDeviceState().getCameraState(),
                                                                 state.getVideoState().getLocalSink() != null ? state.getVideoState().getLocalSink()
                                                                                                              : new BroadcastVideoSink(null),
                                                                 state.getLocalDeviceState().isMicrophoneEnabled());
  }

  public @NonNull State getState() {
    return state;
  }

  public @NonNull GroupCallState getGroupState() {
    return groupState;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public boolean isRemoteVideoEnabled() {
    return Stream.of(remoteParticipants).anyMatch(CallParticipant::isVideoEnabled) || (groupState.isNotIdle() && remoteParticipants.size() > 1);
  }

  public boolean isBluetoothAvailable() {
    return isBluetoothAvailable;
  }

  public boolean isRemoteVideoOffer() {
    return isRemoteVideoOffer;
  }

  public long getCallConnectedTime() {
    return callConnectedTime;
  }

  public @NonNull CallParticipant getLocalParticipant() {
    return localParticipant;
  }

  public @NonNull List<CallParticipant> getRemoteParticipants() {
    return remoteParticipants;
  }

  public @NonNull Set<RecipientId> getIdentityChangedParticipants() {
    return identityChangedRecipients;
  }

  public OptionalLong getRemoteDevicesCount() {
    return remoteDevicesCount;
  }

  public @Nullable Long getParticipantLimit() {
    return participantLimit;
  }

  @Override
  public @NonNull String toString() {
    return "WebRtcViewModel{" +
           "state=" + state +
           ", recipient=" + recipient.getId() +
           ", isBluetoothAvailable=" + isBluetoothAvailable +
           ", isRemoteVideoOffer=" + isRemoteVideoOffer +
           ", callConnectedTime=" + callConnectedTime +
           ", localParticipant=" + localParticipant +
           ", remoteParticipants=" + remoteParticipants +
           ", identityChangedRecipients=" + identityChangedRecipients +
           ", remoteDevicesCount=" + remoteDevicesCount +
           ", participantLimit=" + participantLimit +
           '}';
  }
}
