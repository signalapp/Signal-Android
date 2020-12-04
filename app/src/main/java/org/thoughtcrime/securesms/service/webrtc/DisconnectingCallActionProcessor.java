package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

/**
 * Handles disconnecting state actions. This primairly entails dealing with final
 * clean up in the call concluded action, but also allows for transitioning into idle/setup
 * via beginning an outgoing or incoming call.
 */
public class DisconnectingCallActionProcessor extends WebRtcActionProcessor {

  private static final String TAG = Log.tag(DisconnectingCallActionProcessor.class);

  public DisconnectingCallActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleStartIncomingCall():");
    currentState = currentState.builder()
                               .actionProcessor(new IdleActionProcessor(webRtcInteractor))
                               .build();
    return currentState.getActionProcessor().handleStartIncomingCall(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer, @NonNull OfferMessage.Type offerType) {
    Log.i(TAG, "handleOutgoingCall():");
    currentState = currentState.builder()
                               .actionProcessor(new IdleActionProcessor(webRtcInteractor))
                               .build();
    return currentState.getActionProcessor().handleOutgoingCall(currentState, remotePeer, offerType);
  }

  @Override
  protected @NonNull WebRtcServiceState handleCallConcluded(@NonNull WebRtcServiceState currentState, @Nullable RemotePeer remotePeer) {
    Log.i(TAG, "handleCallConcluded():");

    WebRtcServiceStateBuilder builder = currentState.builder()
                                                    .actionProcessor(new IdleActionProcessor(webRtcInteractor));

    if (remotePeer != null) {
      Log.i(TAG, "delete remotePeer callId: " + remotePeer.getCallId() + " key: " + remotePeer.hashCode());

      builder.changeCallInfoState()
             .removeRemotePeer(remotePeer)
             .commit();
    }

    return builder.build();
  }
}
