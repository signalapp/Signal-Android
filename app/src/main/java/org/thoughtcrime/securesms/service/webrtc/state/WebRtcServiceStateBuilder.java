package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.OptionalLong;

import org.signal.ringrtc.CallId;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.components.sensors.Orientation;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.GroupCallSpeechEvent;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.CallLinkDisconnectReason;
import org.thoughtcrime.securesms.service.webrtc.WebRtcActionProcessor;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.webrtc.PeerConnection;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Builder that creates a new {@link WebRtcServiceState} from an existing one and allows
 * changes to all normally immutable data.
 */
public class WebRtcServiceStateBuilder {
  private WebRtcServiceState toBuild;

  public WebRtcServiceStateBuilder(@NonNull WebRtcServiceState webRtcServiceState) {
    toBuild = new WebRtcServiceState(webRtcServiceState);
  }

  public @NonNull WebRtcServiceState build() {
    return toBuild;
  }

  public @NonNull WebRtcServiceStateBuilder actionProcessor(@NonNull WebRtcActionProcessor actionHandler) {
    toBuild.actionProcessor = actionHandler;
    return this;
  }

  public @NonNull CallSetupStateBuilder changeCallSetupState(@NonNull CallId callId) {
    return new CallSetupStateBuilder(callId);
  }

  public @NonNull CallInfoStateBuilder changeCallInfoState() {
    return new CallInfoStateBuilder();
  }

  public @NonNull LocalDeviceStateBuilder changeLocalDeviceState() {
    return new LocalDeviceStateBuilder();
  }

  public @NonNull VideoStateBuilder changeVideoState() {
    return new VideoStateBuilder();
  }

  public @NonNull WebRtcServiceStateBuilder terminate(@NonNull CallId callId) {
    toBuild.localDeviceState = new LocalDeviceState();
    toBuild.videoState       = new VideoState();

    CallInfoState newCallInfoState = new CallInfoState();
    newCallInfoState.getPeerMap().putAll(toBuild.callInfoState.getPeerMap());
    toBuild.callInfoState = newCallInfoState;

    toBuild.callSetupStates.remove(callId);

    return this;
  }

  public class LocalDeviceStateBuilder {
    private LocalDeviceState toBuild;

    public LocalDeviceStateBuilder() {
      toBuild = WebRtcServiceStateBuilder.this.toBuild.localDeviceState.duplicate();
    }

    public @NonNull WebRtcServiceStateBuilder commit() {
      WebRtcServiceStateBuilder.this.toBuild.localDeviceState = toBuild;
      return WebRtcServiceStateBuilder.this;
    }

    public @NonNull WebRtcServiceState build() {
      commit();
      return WebRtcServiceStateBuilder.this.build();
    }

    public @NonNull LocalDeviceStateBuilder cameraState(@NonNull CameraState cameraState) {
      toBuild.setCameraState(cameraState);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder isMicrophoneEnabled(boolean enabled) {
      toBuild.setMicrophoneEnabled(enabled);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setOrientation(@NonNull Orientation orientation) {
      toBuild.setOrientation(orientation);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setLandscapeEnabled(boolean isLandscapeEnabled) {
      toBuild.setLandscapeEnabled(isLandscapeEnabled);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setDeviceOrientation(@NonNull Orientation deviceOrientation) {
      toBuild.setDeviceOrientation(deviceOrientation);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setActiveDevice(@NonNull SignalAudioManager.AudioDevice audioDevice) {
      toBuild.setActiveDevice(audioDevice);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setAvailableDevices(@NonNull Set<SignalAudioManager.AudioDevice> availableDevices) {
      toBuild.setAvailableDevices(availableDevices);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setBluetoothPermissionDenied(boolean bluetoothPermissionDenied) {
      toBuild.setBluetoothPermissionDenied(bluetoothPermissionDenied);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setNetworkConnectionType(@NonNull PeerConnection.AdapterType type) {
      toBuild.setNetworkConnectionType(type);
      return this;
    }

    public @NonNull LocalDeviceStateBuilder setHandRaisedTimestamp(long handRaisedTimestamp) {
      toBuild.setHandRaisedTimestamp(handRaisedTimestamp);
      return this;
    }
  }

  public class CallSetupStateBuilder {
    private CallSetupState toBuild;
    private CallId         callId;

    public CallSetupStateBuilder(@NonNull CallId callId) {
      this.toBuild = WebRtcServiceStateBuilder.this.toBuild.getCallSetupState(callId).duplicate();
      this.callId  = callId;
    }

    public @NonNull WebRtcServiceStateBuilder commit() {
      WebRtcServiceStateBuilder.this.toBuild.callSetupStates.put(callId, toBuild);
      return WebRtcServiceStateBuilder.this;
    }

    public @NonNull WebRtcServiceState build() {
      commit();
      return WebRtcServiceStateBuilder.this.build();
    }

    public @NonNull CallSetupStateBuilder enableVideoOnCreate(boolean enableVideoOnCreate) {
      toBuild.setEnableVideoOnCreate(enableVideoOnCreate);
      return this;
    }

    public @NonNull CallSetupStateBuilder isRemoteVideoOffer(boolean isRemoteVideoOffer) {
      toBuild.setRemoteVideoOffer(isRemoteVideoOffer);
      return this;
    }

    public @NonNull CallSetupStateBuilder acceptWithVideo(boolean acceptWithVideo) {
      toBuild.setAcceptWithVideo(acceptWithVideo);
      return this;
    }

    public @NonNull CallSetupStateBuilder sentJoinedMessage(boolean sentJoinedMessage) {
      toBuild.setSentJoinedMessage(sentJoinedMessage);
      return this;
    }

    public @NonNull CallSetupStateBuilder setRingGroup(boolean ringGroup) {
      toBuild.setRingGroup(ringGroup);
      return this;
    }

    public @NonNull CallSetupStateBuilder ringId(long ringId) {
      toBuild.setRingId(ringId);
      return this;
    }

    public @NonNull CallSetupStateBuilder ringerRecipient(@NonNull Recipient ringerRecipient) {
      toBuild.setRingerRecipient(ringerRecipient);
      return this;
    }

    public @NonNull CallSetupStateBuilder waitForTelecom(boolean waitForTelecom) {
      toBuild.setWaitForTelecom(waitForTelecom);
      return this;
    }

    public @NonNull CallSetupStateBuilder telecomApproved(boolean telecomApproved) {
      toBuild.setTelecomApproved(telecomApproved);
      return this;
    }

    public @NonNull CallSetupStateBuilder iceServers(Collection<PeerConnection.IceServer> iceServers) {
      toBuild.getIceServers().clear();
      toBuild.getIceServers().addAll(iceServers);
      return this;
    }

    public @NonNull CallSetupStateBuilder alwaysTurn(boolean isAlwaysTurn) {
      toBuild.setAlwaysTurnServers(isAlwaysTurn);
      return this;
    }
  }

  public class VideoStateBuilder {
    private VideoState toBuild;

    public VideoStateBuilder() {
      toBuild = new VideoState(WebRtcServiceStateBuilder.this.toBuild.videoState);
    }

    public @NonNull WebRtcServiceStateBuilder commit() {
      WebRtcServiceStateBuilder.this.toBuild.videoState = toBuild;
      return WebRtcServiceStateBuilder.this;
    }

    public @NonNull WebRtcServiceState build() {
      commit();
      return WebRtcServiceStateBuilder.this.build();
    }

    public @NonNull VideoStateBuilder eglBase(@Nullable EglBaseWrapper eglBase) {
      toBuild.eglBase = eglBase;
      return this;
    }

    public @NonNull VideoStateBuilder localSink(@Nullable BroadcastVideoSink localSink) {
      toBuild.localSink = localSink;
      return this;
    }

    public @NonNull VideoStateBuilder camera(@Nullable Camera camera) {
      toBuild.camera = camera;
      return this;
    }
  }

  public class CallInfoStateBuilder {
    private CallInfoState toBuild;

    public CallInfoStateBuilder() {
      toBuild = WebRtcServiceStateBuilder.this.toBuild.callInfoState.duplicate();
    }

    public @NonNull WebRtcServiceStateBuilder commit() {
      WebRtcServiceStateBuilder.this.toBuild.callInfoState = toBuild;
      return WebRtcServiceStateBuilder.this;
    }

    public @NonNull WebRtcServiceState build() {
      commit();
      return WebRtcServiceStateBuilder.this.build();
    }

    public @NonNull CallInfoStateBuilder callState(@NonNull WebRtcViewModel.State callState) {
      toBuild.setCallState(callState);
      return this;
    }

    public @NonNull CallInfoStateBuilder callRecipient(@NonNull Recipient callRecipient) {
      toBuild.setCallRecipient(callRecipient);
      return this;
    }

    public @NonNull CallInfoStateBuilder callConnectedTime(long callConnectedTime) {
      toBuild.setCallConnectedTime(callConnectedTime);
      return this;
    }

    public @NonNull CallInfoStateBuilder putParticipant(@NonNull CallParticipantId callParticipantId, @NonNull CallParticipant callParticipant) {
      toBuild.getRemoteCallParticipantsMap().put(callParticipantId, callParticipant);
      return this;
    }

    public @NonNull CallInfoStateBuilder putParticipant(@NonNull Recipient recipient, @NonNull CallParticipant callParticipant) {
      toBuild.getRemoteCallParticipantsMap().put(new CallParticipantId(recipient), callParticipant);
      return this;
    }

    public @NonNull CallInfoStateBuilder clearParticipantMap() {
      toBuild.getRemoteCallParticipantsMap().clear();
      return this;
    }

    public @NonNull CallInfoStateBuilder putRemotePeer(@NonNull RemotePeer remotePeer) {
      toBuild.getPeerMap().put(remotePeer.hashCode(), remotePeer);
      return this;
    }

    public @NonNull CallInfoStateBuilder clearPeerMap() {
      toBuild.getPeerMap().clear();
      return this;
    }

    public @NonNull CallInfoStateBuilder removeRemotePeer(@NonNull RemotePeer remotePeer) {
      toBuild.getPeerMap().remove(remotePeer.hashCode());
      return this;
    }

    public @NonNull CallInfoStateBuilder activePeer(@Nullable RemotePeer activePeer) {
      toBuild.setActivePeer(activePeer);
      return this;
    }

    public @NonNull CallInfoStateBuilder groupCall(@Nullable GroupCall groupCall) {
      toBuild.setGroupCall(groupCall);
      return this;
    }

    public @NonNull CallInfoStateBuilder groupCallState(@NonNull WebRtcViewModel.GroupCallState groupState) {
      toBuild.setGroupState(groupState);
      return this;
    }

    public @NonNull CallInfoStateBuilder addIdentityChangedRecipients(@NonNull Collection<RecipientId> id) {
      toBuild.getIdentityChangedRecipients().addAll(id);
      return this;
    }

    public @NonNull CallInfoStateBuilder removeIdentityChangedRecipients(@NonNull Collection<RecipientId> ids) {
      toBuild.getIdentityChangedRecipients().removeAll(ids);
      return this;
    }

    public @NonNull CallInfoStateBuilder remoteDevicesCount(long remoteDevicesCount) {
      toBuild.setRemoteDevicesCount(OptionalLong.of(remoteDevicesCount));
      return this;
    }

    public @NonNull CallInfoStateBuilder participantLimit(@Nullable Long participantLimit) {
      toBuild.setParticipantLimit(participantLimit);
      return this;
    }

    public @NonNull CallInfoStateBuilder setCallLinkPendingParticipants(@NonNull List<Recipient> pendingParticipants) {
      toBuild.setPendingParticipants(toBuild.getPendingParticipants().withRecipients(pendingParticipants));
      return this;
    }

    public @NonNull CallInfoStateBuilder setCallLinkPendingParticipantApproved(@NonNull Recipient participant) {
      toBuild.setPendingParticipants(toBuild.getPendingParticipants().withApproval(participant));
      return this;
    }

    public @NonNull CallInfoStateBuilder setCallLinkPendingParticipantRejected(@NonNull Recipient participant) {
      toBuild.setPendingParticipants(toBuild.getPendingParticipants().withDenial(participant));
      return this;
    }

    public @NonNull CallInfoStateBuilder setCallLinkDisconnectReason(@Nullable CallLinkDisconnectReason callLinkDisconnectReason) {
      toBuild.setCallLinkDisconnectReason(callLinkDisconnectReason);
      return this;
    }

    public @NonNull CallInfoStateBuilder setGroupCallEndReason(@Nullable GroupCall.GroupCallEndReason groupCallEndReason) {
      toBuild.setGroupCallEndReason(groupCallEndReason);
      return this;
    }

    public @NonNull CallInfoStateBuilder setGroupCallSpeechEvent(@Nullable GroupCallSpeechEvent groupCallSpeechEvent) {
      toBuild.setGroupCallSpeechEvent(groupCallSpeechEvent);
      return this;
    }
  }
}
