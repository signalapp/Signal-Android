package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.OptionalLong;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.service.webrtc.collections.ParticipantCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the state of all participants, remote and local, combined with view state
 * needed to properly render the participants. The view state primarily consists of
 * if we are in System PIP mode and if we should show our video for an outgoing call.
 */
public final class CallParticipantsState {

  private static final int SMALL_GROUP_MAX = 6;

  public static final CallParticipantsState STARTING_STATE  = new CallParticipantsState(WebRtcViewModel.State.CALL_DISCONNECTED,
                                                                                        WebRtcViewModel.GroupCallState.IDLE,
                                                                                        new ParticipantCollection(SMALL_GROUP_MAX),
                                                                                        CallParticipant.createLocal(CameraState.UNKNOWN, new BroadcastVideoSink(null), false),
                                                                                        null,
                                                                                        WebRtcLocalRenderState.GONE,
                                                                                        false,
                                                                                        false,
                                                                                        false,
                                                                                        OptionalLong.empty());

  private final WebRtcViewModel.State          callState;
  private final WebRtcViewModel.GroupCallState groupCallState;
  private final ParticipantCollection          remoteParticipants;
  private final CallParticipant                localParticipant;
  private final CallParticipant                focusedParticipant;
  private final WebRtcLocalRenderState         localRenderState;
  private final boolean                        isInPipMode;
  private final boolean                        showVideoForOutgoing;
  private final boolean                        isViewingFocusedParticipant;
  private final OptionalLong                   remoteDevicesCount;

  public CallParticipantsState(@NonNull WebRtcViewModel.State callState,
                               @NonNull WebRtcViewModel.GroupCallState groupCallState,
                               @NonNull ParticipantCollection remoteParticipants,
                               @NonNull CallParticipant localParticipant,
                               @Nullable CallParticipant focusedParticipant,
                               @NonNull WebRtcLocalRenderState localRenderState,
                               boolean isInPipMode,
                               boolean showVideoForOutgoing,
                               boolean isViewingFocusedParticipant,
                               OptionalLong remoteDevicesCount)
  {
    this.callState                   = callState;
    this.groupCallState              = groupCallState;
    this.remoteParticipants          = remoteParticipants;
    this.localParticipant            = localParticipant;
    this.localRenderState            = localRenderState;
    this.focusedParticipant          = focusedParticipant;
    this.isInPipMode                 = isInPipMode;
    this.showVideoForOutgoing        = showVideoForOutgoing;
    this.isViewingFocusedParticipant = isViewingFocusedParticipant;
    this.remoteDevicesCount          = remoteDevicesCount;
  }

  public @NonNull WebRtcViewModel.State getCallState() {
    return callState;
  }

  public @NonNull WebRtcViewModel.GroupCallState getGroupCallState() {
    return groupCallState;
  }

  public @NonNull List<CallParticipant> getGridParticipants() {
    return remoteParticipants.getGridParticipants();
  }

  public @NonNull List<CallParticipant> getListParticipants() {
    List<CallParticipant> listParticipants = new ArrayList<>();

    if (isViewingFocusedParticipant && getAllRemoteParticipants().size() > 1) {
      listParticipants.addAll(getAllRemoteParticipants());
      listParticipants.remove(focusedParticipant);
    } else {
      listParticipants.addAll(remoteParticipants.getListParticipants());
    }

    listParticipants.add(CallParticipant.EMPTY);
    Collections.reverse(listParticipants);

    return listParticipants;
  }

  public @NonNull String getRemoteParticipantsDescription(@NonNull Context context) {
    switch (remoteParticipants.size()) {
      case 0:
        return context.getString(R.string.WebRtcCallView__no_one_else_is_here);
      case 1:
        if (callState == WebRtcViewModel.State.CALL_PRE_JOIN && groupCallState.isNotIdle()) {
          return context.getString(R.string.WebRtcCallView__s_is_in_this_call, remoteParticipants.get(0).getShortRecipientDisplayName(context));
        } else {
          return remoteParticipants.get(0).getRecipientDisplayName(context);
        }
      case 2:
        return context.getString(R.string.WebRtcCallView__s_and_s_are_in_this_call,
                                 remoteParticipants.get(0).getShortRecipientDisplayName(context),
                                 remoteParticipants.get(1).getShortRecipientDisplayName(context));
      default:
        int others = remoteParticipants.size() - 2;
        return context.getResources().getQuantityString(R.plurals.WebRtcCallView__s_s_and_d_others_are_in_this_call,
                                                        others,
                                                        remoteParticipants.get(0).getShortRecipientDisplayName(context),
                                                        remoteParticipants.get(1).getShortRecipientDisplayName(context),
                                                        others);
    }
  }

  public @NonNull List<CallParticipant> getAllRemoteParticipants() {
    return remoteParticipants.getAllParticipants();
  }

  public @NonNull CallParticipant getLocalParticipant() {
    return localParticipant;
  }

  public @Nullable CallParticipant getFocusedParticipant() {
    return focusedParticipant;
  }

  public @NonNull WebRtcLocalRenderState getLocalRenderState() {
    return localRenderState;
  }

  public boolean isLargeVideoGroup() {
    return getAllRemoteParticipants().size() > SMALL_GROUP_MAX;
  }

  public boolean isInPipMode() {
    return isInPipMode;
  }

  public boolean needsNewRequestSizes() {
    return Stream.of(getAllRemoteParticipants()).anyMatch(p -> p.getVideoSink().needsNewRequestingSize());
  }

  public @NonNull OptionalLong getRemoteDevicesCount() {
    return remoteDevicesCount;
  }

  public @NonNull OptionalLong getParticipantCount() {
    boolean includeSelf = groupCallState == WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED;

    return remoteDevicesCount.map(l -> l + (includeSelf ? 1L : 0L))
                             .or(() -> includeSelf ? OptionalLong.of(1L) : OptionalLong.empty());
  }

  public static @NonNull CallParticipantsState update(@NonNull CallParticipantsState oldState,
                                                      @NonNull WebRtcViewModel webRtcViewModel,
                                                      boolean enableVideo)
  {
    boolean newShowVideoForOutgoing = oldState.showVideoForOutgoing;
    if (enableVideo) {
      newShowVideoForOutgoing = webRtcViewModel.getState() == WebRtcViewModel.State.CALL_OUTGOING;
    } else if (webRtcViewModel.getState() != WebRtcViewModel.State.CALL_OUTGOING) {
      newShowVideoForOutgoing = false;
    }

    WebRtcLocalRenderState localRenderState = determineLocalRenderMode(webRtcViewModel.getLocalParticipant(),
                                                                       oldState.isInPipMode,
                                                                       newShowVideoForOutgoing,
                                                                       webRtcViewModel.getGroupState().isNotIdle(),
                                                                       webRtcViewModel.getState(),
                                                                       webRtcViewModel.getRemoteParticipants().size(),
                                                                       oldState.isViewingFocusedParticipant,
                                                                       oldState.getLocalRenderState() == WebRtcLocalRenderState.EXPANDED);

    List<CallParticipant> participantsByLastSpoke = new ArrayList<>(webRtcViewModel.getRemoteParticipants());
    Collections.sort(participantsByLastSpoke, ComparatorCompat.reversed((p1, p2) -> Long.compare(p1.getLastSpoke(), p2.getLastSpoke())));

    CallParticipant focused = participantsByLastSpoke.isEmpty() ? null : participantsByLastSpoke.get(0);

    return new CallParticipantsState(webRtcViewModel.getState(),
                                     webRtcViewModel.getGroupState(),
                                     oldState.remoteParticipants.getNext(webRtcViewModel.getRemoteParticipants()),
                                     webRtcViewModel.getLocalParticipant(),
                                     focused,
                                     localRenderState,
                                     oldState.isInPipMode,
                                     newShowVideoForOutgoing,
                                     oldState.isViewingFocusedParticipant,
                                     webRtcViewModel.getRemoteDevicesCount());
  }

  public static @NonNull CallParticipantsState update(@NonNull CallParticipantsState oldState, boolean isInPip) {
    WebRtcLocalRenderState localRenderState = determineLocalRenderMode(oldState.localParticipant,
                                                                       isInPip,
                                                                       oldState.showVideoForOutgoing,
                                                                       oldState.getGroupCallState().isNotIdle(),
                                                                       oldState.callState,
                                                                       oldState.getAllRemoteParticipants().size(),
                                                                       oldState.isViewingFocusedParticipant,
                                                                       oldState.getLocalRenderState() == WebRtcLocalRenderState.EXPANDED);

    CallParticipant focused = oldState.remoteParticipants.isEmpty() ? null : oldState.remoteParticipants.get(0);

    return new CallParticipantsState(oldState.callState,
                                     oldState.groupCallState,
                                     oldState.remoteParticipants,
                                     oldState.localParticipant,
                                     focused,
                                     localRenderState,
                                     isInPip,
                                     oldState.showVideoForOutgoing,
                                     oldState.isViewingFocusedParticipant,
                                     oldState.remoteDevicesCount);
  }

  public static @NonNull CallParticipantsState setExpanded(@NonNull CallParticipantsState oldState, boolean expanded) {
    WebRtcLocalRenderState localRenderState = determineLocalRenderMode(oldState.localParticipant,
                                                                       oldState.isInPipMode,
                                                                       oldState.showVideoForOutgoing,
                                                                       oldState.getGroupCallState().isNotIdle(),
                                                                       oldState.callState,
                                                                       oldState.getAllRemoteParticipants().size(),
                                                                       oldState.isViewingFocusedParticipant,
                                                                       expanded);

    return new CallParticipantsState(oldState.callState,
                                     oldState.groupCallState,
                                     oldState.remoteParticipants,
                                     oldState.localParticipant,
                                     oldState.focusedParticipant,
                                     localRenderState,
                                     oldState.isInPipMode,
                                     oldState.showVideoForOutgoing,
                                     oldState.isViewingFocusedParticipant,
                                     oldState.remoteDevicesCount);
  }

  public static @NonNull CallParticipantsState update(@NonNull CallParticipantsState oldState, @NonNull SelectedPage selectedPage) {
    CallParticipant focused = oldState.remoteParticipants.isEmpty() ? null : oldState.remoteParticipants.get(0);

    WebRtcLocalRenderState localRenderState = determineLocalRenderMode(oldState.localParticipant,
                                                                       oldState.isInPipMode,
                                                                       oldState.showVideoForOutgoing,
                                                                       oldState.getGroupCallState().isNotIdle(),
                                                                       oldState.callState,
                                                                       oldState.getAllRemoteParticipants().size(),
                                                                       selectedPage == SelectedPage.FOCUSED,
                                                                       oldState.getLocalRenderState() == WebRtcLocalRenderState.EXPANDED);

    return new CallParticipantsState(oldState.callState,
                                     oldState.groupCallState,
                                     oldState.remoteParticipants,
                                     oldState.localParticipant,
                                     focused,
                                     localRenderState,
                                     oldState.isInPipMode,
                                     oldState.showVideoForOutgoing,
                                     selectedPage == SelectedPage.FOCUSED,
                                     oldState.remoteDevicesCount);
  }

  private static @NonNull WebRtcLocalRenderState determineLocalRenderMode(@NonNull CallParticipant localParticipant,
                                                                          boolean isInPip,
                                                                          boolean showVideoForOutgoing,
                                                                          boolean isNonIdleGroupCall,
                                                                          @NonNull WebRtcViewModel.State callState,
                                                                          int numberOfRemoteParticipants,
                                                                          boolean isViewingFocusedParticipant,
                                                                          boolean isExpanded)
  {
    boolean                displayLocal     = (numberOfRemoteParticipants == 0 || !isInPip) && (isNonIdleGroupCall || localParticipant.isVideoEnabled());
    WebRtcLocalRenderState localRenderState = WebRtcLocalRenderState.GONE;

    if (isExpanded && (localParticipant.isVideoEnabled() || isNonIdleGroupCall)) {
      return WebRtcLocalRenderState.EXPANDED;
    } else if (displayLocal || showVideoForOutgoing) {
      if (callState == WebRtcViewModel.State.CALL_CONNECTED) {
        if (isViewingFocusedParticipant || numberOfRemoteParticipants > 1) {
          localRenderState = WebRtcLocalRenderState.SMALLER_RECTANGLE;
        } else if (numberOfRemoteParticipants == 1) {
          localRenderState = WebRtcLocalRenderState.SMALL_RECTANGLE;
        } else {
          localRenderState = localParticipant.isVideoEnabled() ? WebRtcLocalRenderState.LARGE : WebRtcLocalRenderState.LARGE_NO_VIDEO;
        }
      } else if (callState != WebRtcViewModel.State.CALL_INCOMING && callState != WebRtcViewModel.State.CALL_DISCONNECTED) {
        localRenderState = localParticipant.isVideoEnabled() ? WebRtcLocalRenderState.LARGE : WebRtcLocalRenderState.LARGE_NO_VIDEO;
      }
    } else if (callState == WebRtcViewModel.State.CALL_PRE_JOIN) {
      localRenderState = WebRtcLocalRenderState.LARGE_NO_VIDEO;
    }

    return localRenderState;
  }

  public enum SelectedPage {
    GRID,
    FOCUSED
  }
}
