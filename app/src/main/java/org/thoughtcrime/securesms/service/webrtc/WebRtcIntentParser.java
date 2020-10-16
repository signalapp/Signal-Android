package org.thoughtcrime.securesms.service.webrtc;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallId;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.ringrtc.TurnServerInfoParcel;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.webrtc.PeerConnection;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_ANSWER_OPAQUE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_ANSWER_SDP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_AVAILABLE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_BROADCAST;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_CALL_ID;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_ENABLE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_ICE_CANDIDATES;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_MULTI_RING;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_OFFER_OPAQUE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_OFFER_SDP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_OFFER_TYPE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_REMOTE_DEVICE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_REMOTE_IDENTITY_KEY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_REMOTE_PEER_KEY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_TURN_SERVER_INFO;

/**
 * Helper to parse the various attributes out of intents passed to the service.
 */
public final class WebRtcIntentParser {

  private static final String TAG = Log.tag(WebRtcIntentParser.class);

  private WebRtcIntentParser() {}

  public static @NonNull CallId getCallId(@NonNull Intent intent) {
    return new CallId(intent.getLongExtra(EXTRA_CALL_ID, -1));
  }

  public static int getRemoteDevice(@NonNull Intent intent) {
    return intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
  }

  public static @NonNull RemotePeer getRemotePeer(@NonNull Intent intent) {
    RemotePeer remotePeer = intent.getParcelableExtra(WebRtcCallService.EXTRA_REMOTE_PEER);
    if (remotePeer == null) {
      throw new AssertionError("No RemotePeer in intent!");
    }
    return remotePeer;
  }

  public static @NonNull RemotePeer getRemotePeerFromMap(@NonNull Intent intent, @NonNull WebRtcServiceState currentState) {
    int        remotePeerKey = getRemotePeerKey(intent);
    RemotePeer remotePeer    = currentState.getCallInfoState().getPeer(remotePeerKey);

    if (remotePeer == null) {
      throw new AssertionError("No RemotePeer in map for key: " + remotePeerKey + "!");
    }

    return remotePeer;
  }

  public static int getRemotePeerKey(@NonNull Intent intent) {
    if (!intent.hasExtra(EXTRA_REMOTE_PEER_KEY)) {
      throw new AssertionError("No RemotePeer key in intent!");
    }

    // The default of -1 should never be applied since the key exists.
    return intent.getIntExtra(EXTRA_REMOTE_PEER_KEY, -1);
  }

  public static boolean getMultiRingFlag(@NonNull Intent intent) {
    return intent.getBooleanExtra(EXTRA_MULTI_RING, false);
  }

  public static @NonNull byte[] getRemoteIdentityKey(@NonNull Intent intent) {
    return Objects.requireNonNull(intent.getByteArrayExtra(EXTRA_REMOTE_IDENTITY_KEY));
  }

  public static @Nullable String getAnswerSdp(@NonNull Intent intent) {
    return intent.getStringExtra(EXTRA_ANSWER_SDP);
  }

  public static @Nullable String getOfferSdp(@NonNull Intent intent) {
    return intent.getStringExtra(EXTRA_OFFER_SDP);
  }

  public static @Nullable byte[] getAnswerOpaque(@NonNull Intent intent) {
    return intent.getByteArrayExtra(EXTRA_ANSWER_OPAQUE);
  }

  public static @Nullable byte[] getOfferOpaque(@NonNull Intent intent) {
    return intent.getByteArrayExtra(EXTRA_OFFER_OPAQUE);
  }

  public static boolean getBroadcastFlag(@NonNull Intent intent) {
    return intent.getBooleanExtra(EXTRA_BROADCAST, false);
  }

  public static boolean getAvailable(@NonNull Intent intent) {
    return intent.getBooleanExtra(EXTRA_AVAILABLE, false);
  }

  public static @NonNull ArrayList<IceCandidateParcel> getIceCandidates(@NonNull Intent intent) {
    return Objects.requireNonNull(intent.getParcelableArrayListExtra(EXTRA_ICE_CANDIDATES));
  }

  public static @NonNull List<PeerConnection.IceServer> getIceServers(@NonNull Intent intent) {
    TurnServerInfoParcel turnServerInfoParcel = Objects.requireNonNull(intent.getParcelableExtra(EXTRA_TURN_SERVER_INFO));
    List<PeerConnection.IceServer> iceServers = new LinkedList<>();
    iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
    for (String url : turnServerInfoParcel.getUrls()) {
      Log.i(TAG, "ice_server: " + url);
      if (url.startsWith("turn")) {
        iceServers.add(PeerConnection.IceServer.builder(url)
                                               .setUsername(turnServerInfoParcel.getUsername())
                                               .setPassword(turnServerInfoParcel.getPassword())
                                               .createIceServer());
      } else {
        iceServers.add(PeerConnection.IceServer.builder(url).createIceServer());
      }
    }
    return iceServers;
  }

  public static @NonNull OfferMessage.Type getOfferMessageType(@NonNull Intent intent) {
    return OfferMessage.Type.fromCode(intent.getStringExtra(EXTRA_OFFER_TYPE));
  }

  public static boolean getEnable(@NonNull Intent intent) {
    return intent.getBooleanExtra(EXTRA_ENABLE, false);
  }

}
