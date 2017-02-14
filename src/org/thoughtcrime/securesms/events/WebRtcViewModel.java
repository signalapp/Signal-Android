package org.thoughtcrime.securesms.events;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.IdentityKey;

public class WebRtcViewModel {

  public enum State {
    // Normal states
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_CONNECTED,
    CALL_RINGING,
    CALL_BUSY,
    CALL_DISCONNECTED,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY,
  }


  private final @NonNull  State       state;
  private final @NonNull  Recipient   recipient;
  private final @Nullable IdentityKey identityKey;

  private final boolean remoteVideoEnabled;
  private final boolean localVideoEnabled;

  public WebRtcViewModel(@NonNull State state, @NonNull Recipient recipient, boolean localVideoEnabled, boolean remoteVideoEnabled) {
    this(state, recipient, null, localVideoEnabled, remoteVideoEnabled);
  }

  public WebRtcViewModel(@NonNull State state, @NonNull Recipient recipient,
                         @Nullable IdentityKey identityKey,
                         boolean localVideoEnabled, boolean remoteVideoEnabled)
  {
    this.state              = state;
    this.recipient          = recipient;
    this.identityKey        = identityKey;
    this.localVideoEnabled  = localVideoEnabled;
    this.remoteVideoEnabled = remoteVideoEnabled;
  }

  public @NonNull State getState() {
    return state;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  @Nullable
  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public boolean isRemoteVideoEnabled() {
    return remoteVideoEnabled;
  }

  public boolean isLocalVideoEnabled() {
    return localVideoEnabled;
  }

  public String toString() {
    return "[State: " + state + ", recipient: " + recipient.getNumber() + ", identity: " + identityKey + ", remoteVideo: " + remoteVideoEnabled + ", localVideo: " + localVideoEnabled + "]";
  }
}
