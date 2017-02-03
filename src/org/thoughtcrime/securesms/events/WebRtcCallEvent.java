package org.thoughtcrime.securesms.events;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.Recipient;

public class WebRtcCallEvent {

  public enum Type {
    CALL_CONNECTED,
    WAITING_FOR_RESPONDER,
    SERVER_FAILURE,
    PERFORMING_HANDSHAKE,
    HANDSHAKE_FAILED,
    CONNECTING_TO_INITIATOR,
    CALL_DISCONNECTED,
    CALL_RINGING,
    RECIPIENT_UNAVAILABLE,
    INCOMING_CALL,
    OUTGOING_CALL,
    CALL_BUSY,
    LOGIN_FAILED,
    DEBUG_INFO,
    NO_SUCH_USER,
    REMOTE_VIDEO_ENABLED,
    REMOTE_VIDEO_DISABLED,
    UNTRUSTED_IDENTITY
  }

  private final @NonNull  Type      type;
  private final @NonNull  Recipient recipient;
  private final @Nullable Object    extra;

  public WebRtcCallEvent(@NonNull Type type, @NonNull Recipient recipient, @Nullable Object extra) {
    this.type      = type;
    this.recipient = recipient;
    this.extra     = extra;
  }

  public @NonNull Type getType() {
    return type;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @Nullable Object getExtra() {
    return extra;
  }
}
