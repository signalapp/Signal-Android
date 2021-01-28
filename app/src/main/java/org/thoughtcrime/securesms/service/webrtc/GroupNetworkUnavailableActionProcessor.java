package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;

/**
 * Processor which is utilized when the network becomes unavailable during a group call. In general,
 * this is triggered whenever there is a call ended, and the ending was not the result of direct user
 * action.
 *
 * This class will check the network status when handlePreJoinCall is invoked, and transition to
 * GroupPreJoinActionProcessor as network becomes available again.
 */
class GroupNetworkUnavailableActionProcessor extends WebRtcActionProcessor {

  private static final String TAG = Log.tag(GroupNetworkUnavailableActionProcessor.class);

  public GroupNetworkUnavailableActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handlePreJoinCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handlePreJoinCall():");

    ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo         activeNetworkInfo   = connectivityManager.getActiveNetworkInfo();

    if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
      GroupPreJoinActionProcessor processor = new GroupPreJoinActionProcessor(webRtcInteractor);
      return processor.handlePreJoinCall(currentState.builder().actionProcessor(processor).build(), remotePeer);
    }

    byte[]    groupId   = currentState.getCallInfoState().getCallRecipient().requireGroupId().getDecodedId();
    GroupCall groupCall = webRtcInteractor.getCallManager().createGroupCall(groupId,
                                                                            BuildConfig.SIGNAL_SFU_URL,
                                                                            currentState.getVideoState().requireEglBase(),
                                                                            webRtcInteractor.getGroupCallObserver());

    return currentState.builder()
                       .changeCallInfoState()
                       .callState(WebRtcViewModel.State.NETWORK_FAILURE)
                       .groupCall(groupCall)
                       .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleCancelPreJoinCall(@NonNull WebRtcServiceState currentState) {
    Log.i(TAG, "handleCancelPreJoinCall():");

    WebRtcVideoUtil.deinitializeVideo(currentState);

    return new WebRtcServiceState(new IdleActionProcessor(webRtcInteractor));
  }

  @Override
  public @NonNull WebRtcServiceState handleNetworkChanged(@NonNull WebRtcServiceState currentState, boolean available) {
    if (available) {
      return currentState.builder()
                         .actionProcessor(new GroupPreJoinActionProcessor(webRtcInteractor))
                         .changeCallInfoState()
                         .callState(WebRtcViewModel.State.CALL_PRE_JOIN)
                         .build();
    } else {
      return currentState;
    }
  }
}
