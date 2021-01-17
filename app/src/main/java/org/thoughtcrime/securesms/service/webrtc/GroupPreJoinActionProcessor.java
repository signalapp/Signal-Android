package org.thoughtcrime.securesms.service.webrtc;

import android.media.AudioManager;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.PeekInfo;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

import java.util.List;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_OUTGOING_RINGING;

/**
 * Process actions while the user is in the pre-join lobby for the call.
 */
public class GroupPreJoinActionProcessor extends GroupActionProcessor {

  private static final String TAG = Log.tag(GroupPreJoinActionProcessor.class);

  public GroupPreJoinActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handlePreJoinCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handlePreJoinCall():");

    byte[]      groupId = currentState.getCallInfoState().getCallRecipient().requireGroupId().getDecodedId();
    GroupCall groupCall = webRtcInteractor.getCallManager().createGroupCall(groupId,
                                                                            BuildConfig.SIGNAL_SFU_URL,
                                                                            currentState.getVideoState().requireEglBase(),
                                                                            webRtcInteractor.getGroupCallObserver());

    try {
      groupCall.setOutgoingAudioMuted(true);
      groupCall.setOutgoingVideoMuted(true);
      groupCall.setBandwidthMode(NetworkUtil.useLowBandwidthCalling(context) ? GroupCall.BandwidthMode.LOW : GroupCall.BandwidthMode.NORMAL);

      Log.i(TAG, "Connecting to group call: " + currentState.getCallInfoState().getCallRecipient().getId());
      groupCall.connect();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to connect to group call", e);
    }

    SignalStore.tooltips().markGroupCallingLobbyEntered();
    return currentState.builder()
                       .changeCallInfoState()
                       .groupCall(groupCall)
                       .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleCancelPreJoinCall(@NonNull WebRtcServiceState currentState) {
    Log.i(TAG, "handleCancelPreJoinCall():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();
    try {
      groupCall.disconnect();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to disconnect from group call", e);
    }

    WebRtcVideoUtil.deinitializeVideo(currentState);

    return new WebRtcServiceState(new IdleActionProcessor(webRtcInteractor));
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupLocalDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupLocalDeviceStateChanged():");

    GroupCall                  groupCall = currentState.getCallInfoState().requireGroupCall();
    GroupCall.LocalDeviceState device    = groupCall.getLocalDeviceState();

    Log.i(tag, "local device changed: " + device.getConnectionState() + " " + device.getJoinState());

    return currentState.builder()
                       .changeCallInfoState()
                       .groupCallState(WebRtcUtil.groupCallStateForConnection(device.getConnectionState()))
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupJoinedMembershipChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupJoinedMembershipChanged():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();
    PeekInfo  peekInfo  = groupCall.getPeekInfo();

    if (peekInfo == null) {
      Log.i(tag, "No peek info available");
      return currentState;
    }

    List<Recipient> callParticipants = Stream.of(peekInfo.getJoinedMembers())
                                             .map(uuid -> Recipient.externalPush(context, uuid, null, false))
                                             .toList();

    WebRtcServiceStateBuilder.CallInfoStateBuilder builder = currentState.builder()
                                                                         .changeCallInfoState()
                                                                         .remoteDevicesCount(peekInfo.getDeviceCount())
                                                                         .participantLimit(peekInfo.getMaxDevices())
                                                                         .clearParticipantMap();

    for (Recipient recipient : callParticipants) {
      builder.putParticipant(recipient, CallParticipant.createRemote(new CallParticipantId(recipient), recipient, null, new BroadcastVideoSink(null), true, true, 0, false, 0, CallParticipant.DeviceOrdinal.PRIMARY));
    }

    return builder.build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer,
                                                           @NonNull OfferMessage.Type offerType)
  {
    Log.i(TAG, "handleOutgoingCall():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();

    currentState = WebRtcVideoUtil.reinitializeCamera(context, webRtcInteractor.getCameraEventListener(), currentState);

    AudioManager androidAudioManager = ServiceUtil.getAudioManager(context);
    androidAudioManager.setSpeakerphoneOn(false);

    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    webRtcInteractor.initializeAudioForCall();
    webRtcInteractor.setWantsBluetoothConnection(true);
    webRtcInteractor.setCallInProgressNotification(TYPE_OUTGOING_RINGING, currentState.getCallInfoState().getCallRecipient());

    try {
      groupCall.setOutgoingVideoSource(currentState.getVideoState().requireLocalSink(), currentState.getVideoState().requireCamera());
      groupCall.setOutgoingVideoMuted(!currentState.getLocalDeviceState().getCameraState().isEnabled());
      groupCall.setOutgoingAudioMuted(!currentState.getLocalDeviceState().isMicrophoneEnabled());
      groupCall.setBandwidthMode(NetworkUtil.useLowBandwidthCalling(context) ? GroupCall.BandwidthMode.LOW : GroupCall.BandwidthMode.NORMAL);

      groupCall.join();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to join group call", e);
    }

    return currentState.builder()
                       .actionProcessor(new GroupJoiningActionProcessor(webRtcInteractor))
                       .changeCallInfoState()
                       .callState(WebRtcViewModel.State.CALL_OUTGOING)
                       .groupCallState(WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINING)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(TAG, "handleSetEnableVideo(): Changing for pre-join group call. enable: " + enable);

    currentState.getVideoState().requireCamera().setEnabled(enable);
    return currentState.builder()
                       .changeCallSetupState()
                       .enableVideoOnCreate(enable)
                       .commit()
                       .changeLocalDeviceState()
                       .cameraState(currentState.getVideoState().requireCamera().getCameraState())
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    Log.i(TAG, "handleSetMuteAudio(): Changing for pre-join group call. muted: " + muted);

    return currentState.builder()
                       .changeLocalDeviceState()
                       .isMicrophoneEnabled(!muted)
                       .build();
  }

  @Override
  public @NonNull WebRtcServiceState handleNetworkChanged(@NonNull WebRtcServiceState currentState, boolean available) {
    if (!available) {
      return currentState.builder()
                         .actionProcessor(new GroupNetworkUnavailableActionProcessor(webRtcInteractor))
                         .changeCallInfoState()
                         .callState(WebRtcViewModel.State.NETWORK_FAILURE)
                         .build();
    } else {
      return currentState;
    }
  }
}
