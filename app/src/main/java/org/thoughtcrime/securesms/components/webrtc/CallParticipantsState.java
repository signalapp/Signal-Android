package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.ringrtc.CameraState;

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
                                                                                       Collections.emptyList(),
                                                                                       CallParticipant.createLocal(CameraState.UNKNOWN, new BroadcastVideoSink(null), false),
                                                                                       null,
                                                                                       WebRtcLocalRenderState.GONE,
                                                                                       false,
                                                                                       false,
                                                                                       false);

  private final WebRtcViewModel.State  callState;
  private final List<CallParticipant>  remoteParticipants;
  private final CallParticipant        localParticipant;
  private final CallParticipant        focusedParticipant;
  private final WebRtcLocalRenderState localRenderState;
  private final boolean                isInPipMode;
  private final boolean                showVideoForOutgoing;
  private final boolean                isViewingFocusedParticipant;

  public CallParticipantsState(@NonNull WebRtcViewModel.State callState,
                               @NonNull List<CallParticipant> remoteParticipants,
                               @NonNull CallParticipant localParticipant,
                               @Nullable CallParticipant focusedParticipant,
                               @NonNull WebRtcLocalRenderState localRenderState,
                               boolean isInPipMode,
                               boolean showVideoForOutgoing,
                               boolean isViewingFocusedParticipant)
  {
    this.callState                   = callState;
    this.remoteParticipants          = remoteParticipants;
    this.localParticipant            = localParticipant;
    this.localRenderState            = localRenderState;
    this.focusedParticipant          = focusedParticipant;
    this.isInPipMode                 = isInPipMode;
    this.showVideoForOutgoing        = showVideoForOutgoing;
    this.isViewingFocusedParticipant = isViewingFocusedParticipant;
  }

  public @NonNull WebRtcViewModel.State getCallState() {
    return callState;
  }

  public @NonNull List<CallParticipant> getGridParticipants() {
    if (getAllRemoteParticipants().size() > SMALL_GROUP_MAX) {
      return getAllRemoteParticipants().subList(0, SMALL_GROUP_MAX);
    } else {
      return getAllRemoteParticipants();
    }
  }

  public @NonNull List<CallParticipant> getListParticipants() {
    List<CallParticipant> listParticipants = new ArrayList<>();

    if (isViewingFocusedParticipant && getAllRemoteParticipants().size() > 1) {
      listParticipants.addAll(getAllRemoteParticipants().subList(1, getAllRemoteParticipants().size()));
    } else if (getAllRemoteParticipants().size() > SMALL_GROUP_MAX) {
      listParticipants.addAll(getAllRemoteParticipants().subList(SMALL_GROUP_MAX, getAllRemoteParticipants().size()));
    } else {
      return Collections.emptyList();
    }

    listParticipants.add(CallParticipant.EMPTY);

    Collections.reverse(listParticipants);

    return listParticipants;
  }

  public @NonNull List<CallParticipant> getAllRemoteParticipants() {
    return remoteParticipants;
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
                                                                       webRtcViewModel.getState(),
                                                                       oldState.getAllRemoteParticipants().size(),
                                                                       oldState.isViewingFocusedParticipant);

    CallParticipant focused = oldState.remoteParticipants.isEmpty() ? null : oldState.remoteParticipants.get(0);

    return new CallParticipantsState(webRtcViewModel.getState(),
                                     webRtcViewModel.getRemoteParticipants(),
                                     webRtcViewModel.getLocalParticipant(),
                                     focused,
                                     localRenderState,
                                     oldState.isInPipMode,
                                     newShowVideoForOutgoing,
                                     oldState.isViewingFocusedParticipant);
  }

  public static @NonNull CallParticipantsState update(@NonNull CallParticipantsState oldState, boolean isInPip) {
    WebRtcLocalRenderState localRenderState = determineLocalRenderMode(oldState.localParticipant,
                                                                       isInPip,
                                                                       oldState.showVideoForOutgoing,
                                                                       oldState.callState,
                                                                       oldState.getAllRemoteParticipants().size(),
                                                                       oldState.isViewingFocusedParticipant);

    CallParticipant focused = oldState.remoteParticipants.isEmpty() ? null : oldState.remoteParticipants.get(0);

    return new CallParticipantsState(oldState.callState,
                                     oldState.remoteParticipants,
                                     oldState.localParticipant,
                                     focused,
                                     localRenderState,
                                     isInPip,
                                     oldState.showVideoForOutgoing,
                                     oldState.isViewingFocusedParticipant);
  }

  public static @NonNull CallParticipantsState update(@NonNull CallParticipantsState oldState, @NonNull SelectedPage selectedPage) {
    CallParticipant focused = oldState.remoteParticipants.isEmpty() ? null : oldState.remoteParticipants.get(0);

    WebRtcLocalRenderState localRenderState = determineLocalRenderMode(oldState.localParticipant,
                                                                       oldState.isInPipMode,
                                                                       oldState.showVideoForOutgoing,
                                                                       oldState.callState,
                                                                       oldState.getAllRemoteParticipants().size(),
                                                                       selectedPage == SelectedPage.FOCUSED);

    return new CallParticipantsState(oldState.callState,
                                     oldState.remoteParticipants,
                                     oldState.localParticipant,
                                     focused,
                                     localRenderState,
                                     oldState.isInPipMode,
                                     oldState.showVideoForOutgoing,
                                     selectedPage == SelectedPage.FOCUSED);
  }

  private static @NonNull WebRtcLocalRenderState determineLocalRenderMode(@NonNull CallParticipant localParticipant,
                                                                          boolean isInPip,
                                                                          boolean showVideoForOutgoing,
                                                                          @NonNull WebRtcViewModel.State callState,
                                                                          int numberOfRemoteParticipants,
                                                                          boolean isViewingFocusedParticipant)
  {
    boolean                displayLocal     = !isInPip && localParticipant.isVideoEnabled();
    WebRtcLocalRenderState localRenderState = WebRtcLocalRenderState.GONE;

    if (displayLocal || showVideoForOutgoing) {
      if (callState == WebRtcViewModel.State.CALL_CONNECTED) {
        if (isViewingFocusedParticipant || numberOfRemoteParticipants > 3) {
          localRenderState = WebRtcLocalRenderState.SMALL_SQUARE;
        } else {
          localRenderState = WebRtcLocalRenderState.SMALL_RECTANGLE;
        }
      } else if (callState != WebRtcViewModel.State.CALL_DISCONNECTED) {
        localRenderState = WebRtcLocalRenderState.LARGE;
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
