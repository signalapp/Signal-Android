package org.thoughtcrime.securesms.events;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.Recipient;

public class RedPhoneEvent {

  public enum Type {
    CALL_CONNECTED,
    WAITING_FOR_RESPONDER,
    SERVER_FAILURE,
    PERFORMING_HANDSHAKE,
    HANDSHAKE_FAILED,
    CONNECTING_TO_INITIATOR,
    CALL_DISCONNECTED,
    CALL_RINGING,
    SERVER_MESSAGE,
    RECIPIENT_UNAVAILABLE,
    INCOMING_CALL,
    OUTGOING_CALL,
    CALL_BUSY,
    LOGIN_FAILED,
    CLIENT_FAILURE,
    DEBUG_INFO,
    NO_SUCH_USER
  }

  private final @NonNull  Type      type;
  private final @NonNull  Recipient recipient;
  private final @Nullable String    extra;

  public RedPhoneEvent(@NonNull Type type, @NonNull Recipient recipient, @Nullable String extra) {
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

  public @Nullable String getExtra() {
    return extra;
  }
}
