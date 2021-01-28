package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.OptionalLong;

import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * General state of ongoing calls.
 */
public class CallInfoState {

  WebRtcViewModel.State                   callState;
  Recipient                               callRecipient;
  long                                    callConnectedTime;
  Map<CallParticipantId, CallParticipant> remoteParticipants;
  Map<Integer, RemotePeer>                peerMap;
  RemotePeer                              activePeer;
  GroupCall                               groupCall;
  WebRtcViewModel.GroupCallState          groupState;
  Set<RecipientId>                        identityChangedRecipients;
  OptionalLong                            remoteDevicesCount;
  Long                                    participantLimit;

  public CallInfoState() {
    this(WebRtcViewModel.State.IDLE,
         Recipient.UNKNOWN,
         -1,
         Collections.emptyMap(),
         Collections.emptyMap(),
         null,
         null,
         WebRtcViewModel.GroupCallState.IDLE,
         Collections.emptySet(),
         OptionalLong.empty(),
         null);
  }

  public CallInfoState(@NonNull CallInfoState toCopy) {
    this(toCopy.callState,
         toCopy.callRecipient,
         toCopy.callConnectedTime,
         toCopy.remoteParticipants,
         toCopy.peerMap,
         toCopy.activePeer,
         toCopy.groupCall,
         toCopy.groupState,
         toCopy.identityChangedRecipients,
         toCopy.remoteDevicesCount,
         toCopy.participantLimit);
  }

  public CallInfoState(@NonNull WebRtcViewModel.State callState,
                       @NonNull Recipient callRecipient,
                       long callConnectedTime,
                       @NonNull Map<CallParticipantId, CallParticipant> remoteParticipants,
                       @NonNull Map<Integer, RemotePeer> peerMap,
                       @Nullable RemotePeer activePeer,
                       @Nullable GroupCall groupCall,
                       @NonNull WebRtcViewModel.GroupCallState groupState,
                       @NonNull Set<RecipientId> identityChangedRecipients,
                       @NonNull OptionalLong remoteDevicesCount,
                       @Nullable Long participantLimit)
  {
    this.callState                 = callState;
    this.callRecipient             = callRecipient;
    this.callConnectedTime         = callConnectedTime;
    this.remoteParticipants        = new LinkedHashMap<>(remoteParticipants);
    this.peerMap                   = new HashMap<>(peerMap);
    this.activePeer                = activePeer;
    this.groupCall                 = groupCall;
    this.groupState                = groupState;
    this.identityChangedRecipients = new HashSet<>(identityChangedRecipients);
    this.remoteDevicesCount        = remoteDevicesCount;
    this.participantLimit          = participantLimit;
  }

  public @NonNull Recipient getCallRecipient() {
    return callRecipient;
  }

  public long getCallConnectedTime() {
    return callConnectedTime;
  }

  public @NonNull Map<CallParticipantId, CallParticipant> getRemoteCallParticipantsMap() {
    return new LinkedHashMap<>(remoteParticipants);
  }

  public @Nullable CallParticipant getRemoteCallParticipant(@NonNull Recipient recipient) {
    return getRemoteCallParticipant(new CallParticipantId(recipient));
  }

  public @Nullable CallParticipant getRemoteCallParticipant(@NonNull CallParticipantId callParticipantId) {
    return remoteParticipants.get(callParticipantId);
  }

  public @NonNull List<CallParticipant> getRemoteCallParticipants() {
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

  public @Nullable GroupCall getGroupCall() {
    return groupCall;
  }

  public @NonNull GroupCall requireGroupCall() {
    return Objects.requireNonNull(groupCall);
  }

  public @NonNull WebRtcViewModel.GroupCallState getGroupCallState() {
    return groupState;
  }

  public @NonNull Set<RecipientId> getIdentityChangedRecipients() {
    return identityChangedRecipients;
  }

  public OptionalLong getRemoteDevicesCount() {
    return remoteDevicesCount;
  }

  public @Nullable Long getParticipantLimit() {
    return participantLimit;
  }
}
