package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.Collection;
import java.util.UUID;

/**
 * Serves as the bridge between the action processing framework as the WebRTC service. Attempts
 * to minimize direct access to various managers by providing a simple proxy to them. Due to the
 * heavy use of {@link CallManager} throughout, it was exempted from the rule.
 */
public class WebRtcInteractor {

  @NonNull private final Context                        context;
  @NonNull private final SignalCallManager              signalCallManager;
  @NonNull private final LockManager                    lockManager;
  @NonNull private final SignalAudioManager             audioManager;
  @NonNull private final CameraEventListener            cameraEventListener;
  @NonNull private final GroupCall.Observer             groupCallObserver;
  @NonNull private final AppForegroundObserver.Listener foregroundListener;

  public WebRtcInteractor(@NonNull Context context,
                          @NonNull SignalCallManager signalCallManager,
                          @NonNull LockManager lockManager,
                          @NonNull SignalAudioManager audioManager,
                          @NonNull CameraEventListener cameraEventListener,
                          @NonNull GroupCall.Observer groupCallObserver,
                          @NonNull AppForegroundObserver.Listener foregroundListener)
  {
    this.context           = context;
    this.signalCallManager = signalCallManager;
    this.lockManager       = lockManager;
    this.audioManager        = audioManager;
    this.cameraEventListener = cameraEventListener;
    this.groupCallObserver   = groupCallObserver;
    this.foregroundListener  = foregroundListener;
  }

  @NonNull Context getContext() {
    return context;
  }

  @NonNull CameraEventListener getCameraEventListener() {
    return cameraEventListener;
  }

  @NonNull CallManager getCallManager() {
    return signalCallManager.getRingRtcCallManager();
  }

  @NonNull GroupCall.Observer getGroupCallObserver() {
    return groupCallObserver;
  }

  @NonNull AppForegroundObserver.Listener getForegroundListener() {
    return foregroundListener;
  }

  void setWantsBluetoothConnection(boolean enabled) {
    WebRtcCallService.setWantsBluetoothConnection(context, enabled);
  }

  void updatePhoneState(@NonNull LockManager.PhoneState phoneState) {
    lockManager.updatePhoneState(phoneState);
  }

  void postStateUpdate(@NonNull WebRtcServiceState state) {
    signalCallManager.postStateUpdate(state);
  }

  void sendCallMessage(@NonNull RemotePeer remotePeer, @NonNull SignalServiceCallMessage callMessage) {
    signalCallManager.sendCallMessage(remotePeer, callMessage);
  }

  void sendGroupCallMessage(@NonNull Recipient recipient, @Nullable String groupCallEraId) {
    signalCallManager.sendGroupCallUpdateMessage(recipient, groupCallEraId);
  }

  void updateGroupCallUpdateMessage(@NonNull RecipientId groupId, @Nullable String groupCallEraId, @NonNull Collection<UUID> joinedMembers, boolean isCallFull) {
    signalCallManager.updateGroupCallUpdateMessage(groupId, groupCallEraId, joinedMembers, isCallFull);
  }

  void setCallInProgressNotification(int type, @NonNull RemotePeer remotePeer) {
    WebRtcCallService.update(context, type, remotePeer.getRecipient().getId());
  }

  void setCallInProgressNotification(int type, @NonNull Recipient recipient) {
    WebRtcCallService.update(context, type, recipient.getId());
  }

  void retrieveTurnServers(@NonNull RemotePeer remotePeer) {
    signalCallManager.retrieveTurnServers(remotePeer);
  }

  void stopForegroundService() {
    WebRtcCallService.stop(context);
  }

  void insertMissedCall(@NonNull RemotePeer remotePeer, long timestamp, boolean isVideoOffer) {
    signalCallManager.insertMissedCall(remotePeer, true, timestamp, isVideoOffer);
  }

  boolean startWebRtcCallActivityIfPossible() {
    return signalCallManager.startCallCardActivityIfPossible();
  }

  void registerPowerButtonReceiver() {
    WebRtcCallService.changePowerButtonReceiver(context, true);
  }

  void unregisterPowerButtonReceiver() {
    WebRtcCallService.changePowerButtonReceiver(context, false);
  }

  void silenceIncomingRinger() {
    audioManager.silenceIncomingRinger();
  }

  void initializeAudioForCall() {
    audioManager.initializeAudioForCall();
  }

  void startIncomingRinger(@Nullable Uri ringtoneUri, boolean vibrate) {
    audioManager.startIncomingRinger(ringtoneUri, vibrate);
  }

  void startOutgoingRinger() {
    audioManager.startOutgoingRinger(OutgoingRinger.Type.RINGING);
  }

  void stopAudio(boolean playDisconnect) {
    audioManager.stop(playDisconnect);
  }

  void startAudioCommunication(boolean preserveSpeakerphone) {
    audioManager.startCommunication(preserveSpeakerphone);
  }

  void peekGroupCall(@NonNull RecipientId recipientId) {
    signalCallManager.peekGroupCall(recipientId);
  }
}
