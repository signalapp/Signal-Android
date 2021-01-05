package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.OptionalLong;

import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.WebRtcActionProcessor;
import org.webrtc.EglBase;

import java.util.Collection;

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

  public @NonNull CallSetupStateBuilder changeCallSetupState() {
    return new CallSetupStateBuilder();
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

  public @NonNull WebRtcServiceStateBuilder terminate() {
    toBuild.callSetupState   = new CallSetupState();
    toBuild.localDeviceState = new LocalDeviceState();
    toBuild.videoState       = new VideoState();

    CallInfoState newCallInfoState = new CallInfoState();
    newCallInfoState.peerMap.putAll(toBuild.callInfoState.peerMap);
    toBuild.callInfoState = newCallInfoState;

    return this;
  }

  public class LocalDeviceStateBuilder {
    private LocalDeviceState toBuild;

    public LocalDeviceStateBuilder() {
      toBuild = new LocalDeviceState(WebRtcServiceStateBuilder.this.toBuild.localDeviceState);
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
      toBuild.cameraState = cameraState;
      return this;
    }

    public @NonNull LocalDeviceStateBuilder isMicrophoneEnabled(boolean enabled) {
      toBuild.microphoneEnabled = enabled;
      return this;
    }

    public @NonNull LocalDeviceStateBuilder isBluetoothAvailable(boolean available) {
      toBuild.bluetoothAvailable = available;
      return this;
    }
  }

  public class CallSetupStateBuilder {
    private CallSetupState toBuild;

    public CallSetupStateBuilder() {
      toBuild = new CallSetupState(WebRtcServiceStateBuilder.this.toBuild.callSetupState);
    }

    public @NonNull WebRtcServiceStateBuilder commit() {
      WebRtcServiceStateBuilder.this.toBuild.callSetupState = toBuild;
      return WebRtcServiceStateBuilder.this;
    }

    public @NonNull WebRtcServiceState build() {
      commit();
      return WebRtcServiceStateBuilder.this.build();
    }

    public @NonNull CallSetupStateBuilder enableVideoOnCreate(boolean enableVideoOnCreate) {
      toBuild.enableVideoOnCreate = enableVideoOnCreate;
      return this;
    }

    public @NonNull CallSetupStateBuilder isRemoteVideoOffer(boolean isRemoteVideoOffer) {
      toBuild.isRemoteVideoOffer = isRemoteVideoOffer;
      return this;
    }

    public @NonNull CallSetupStateBuilder acceptWithVideo(boolean acceptWithVideo) {
      toBuild.acceptWithVideo = acceptWithVideo;
      return this;
    }

    public @NonNull CallSetupStateBuilder sentJoinedMessage(boolean sentJoinedMessage) {
      toBuild.sentJoinedMessage = sentJoinedMessage;
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

    public @NonNull VideoStateBuilder eglBase(@Nullable EglBase eglBase) {
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
      toBuild = new CallInfoState(WebRtcServiceStateBuilder.this.toBuild.callInfoState);
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
      toBuild.callState = callState;
      return this;
    }

    public @NonNull CallInfoStateBuilder callRecipient(@NonNull Recipient callRecipient) {
      toBuild.callRecipient = callRecipient;
      return this;
    }

    public @NonNull CallInfoStateBuilder callConnectedTime(long callConnectedTime) {
      toBuild.callConnectedTime = callConnectedTime;
      return this;
    }

    public @NonNull CallInfoStateBuilder putParticipant(@NonNull CallParticipantId callParticipantId, @NonNull CallParticipant callParticipant) {
      toBuild.remoteParticipants.put(callParticipantId, callParticipant);
      return this;
    }

    public @NonNull CallInfoStateBuilder putParticipant(@NonNull Recipient recipient, @NonNull CallParticipant callParticipant) {
      toBuild.remoteParticipants.put(new CallParticipantId(recipient), callParticipant);
      return this;
    }

    public @NonNull CallInfoStateBuilder clearParticipantMap() {
      toBuild.remoteParticipants.clear();
      return this;
    }

    public @NonNull CallInfoStateBuilder putRemotePeer(@NonNull RemotePeer remotePeer) {
      toBuild.peerMap.put(remotePeer.hashCode(), remotePeer);
      return this;
    }

    public @NonNull CallInfoStateBuilder clearPeerMap() {
      toBuild.peerMap.clear();
      return this;
    }

    public @NonNull CallInfoStateBuilder removeRemotePeer(@NonNull RemotePeer remotePeer) {
      toBuild.peerMap.remove(remotePeer.hashCode());
      return this;
    }

    public @NonNull CallInfoStateBuilder activePeer(@Nullable RemotePeer activePeer) {
      toBuild.activePeer = activePeer;
      return this;
    }

    public @NonNull CallInfoStateBuilder groupCall(@Nullable GroupCall groupCall) {
      toBuild.groupCall = groupCall;
      return this;
    }

    public @NonNull CallInfoStateBuilder groupCallState(@Nullable WebRtcViewModel.GroupCallState groupState) {
      toBuild.groupState = groupState;
      return this;
    }

    public @NonNull CallInfoStateBuilder addIdentityChangedRecipient(@NonNull RecipientId id) {
      toBuild.identityChangedRecipients.add(id);
      return this;
    }

    public @NonNull CallInfoStateBuilder removeIdentityChangedRecipients(@NonNull Collection<RecipientId> ids) {
      toBuild.identityChangedRecipients.removeAll(ids);
      return this;
    }

    public @NonNull CallInfoStateBuilder remoteDevicesCount(long remoteDevicesCount) {
      toBuild.remoteDevicesCount = OptionalLong.of(remoteDevicesCount);
      return this;
    }

    public @NonNull CallInfoStateBuilder participantLimit(@Nullable Long participantLimit) {
      toBuild.participantLimit = participantLimit;
      return this;
    }
  }
}
