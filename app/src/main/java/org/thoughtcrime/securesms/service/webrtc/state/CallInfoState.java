package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * General state of ongoing calls.
 */
public class CallInfoState {

  WebRtcViewModel.State           callState;
  Recipient                       callRecipient;
  long                            callConnectedTime;
  Map<Recipient, CallParticipant> remoteParticipants;
  Map<Integer, RemotePeer>        peerMap;
  RemotePeer                      activePeer;

  public CallInfoState() {
    this(WebRtcViewModel.State.IDLE, Recipient.UNKNOWN, -1, Collections.emptyMap(), Collections.emptyMap(), null);
  }

  public CallInfoState(@NonNull CallInfoState toCopy) {
    this(toCopy.callState, toCopy.callRecipient, toCopy.callConnectedTime, toCopy.remoteParticipants, toCopy.peerMap, toCopy.activePeer);
  }

  public CallInfoState(@NonNull WebRtcViewModel.State callState,
                       @NonNull Recipient callRecipient,
                       long callConnectedTime,
                       @NonNull Map<Recipient, CallParticipant> remoteParticipants,
                       @NonNull Map<Integer, RemotePeer> peerMap,
                       @Nullable RemotePeer activePeer)
  {
    this.callState          = callState;
    this.callRecipient      = callRecipient;
    this.callConnectedTime  = callConnectedTime;
    this.remoteParticipants = new LinkedHashMap<>(remoteParticipants);
    this.peerMap            = new HashMap<>(peerMap);
    this.activePeer         = activePeer;
  }

  public @NonNull Recipient getCallRecipient() {
    return callRecipient;
  }

  public long getCallConnectedTime() {
    return callConnectedTime;
  }

  public @Nullable CallParticipant getRemoteParticipant(@NonNull Recipient recipient) {
    return remoteParticipants.get(recipient);
  }

  public @NonNull ArrayList<CallParticipant> getRemoteCallParticipants() {
    return new ArrayList<>(remoteParticipants.values());
  }

  public @NonNull WebRtcViewModel.State getCallState() {
    return callState;
  }

  public @Nullable RemotePeer getPeer(int hashCode) {
    return peerMap.get(hashCode);
  }

  public @Nullable RemotePeer getActivePeer() {
    return activePeer;
  }

  public @NonNull RemotePeer requireActivePeer() {
    return Objects.requireNonNull(activePeer);
  }
}
