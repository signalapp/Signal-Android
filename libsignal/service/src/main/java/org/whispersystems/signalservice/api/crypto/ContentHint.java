package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.metadata.protocol.UnidentifiedSenderMessageContent;

import java.util.HashMap;
import java.util.Map;

public enum ContentHint {
  /** This message has content, but you shouldn't expect it to be re-sent to you. */
  DEFAULT(UnidentifiedSenderMessageContent.CONTENT_HINT_DEFAULT),

  /** You should expect to be able to have this content be re-sent to you. */
  RESENDABLE(UnidentifiedSenderMessageContent.CONTENT_HINT_RESENDABLE),

  /** This message has no real content and likely cannot be re-sent to you. */
  IMPLICIT(UnidentifiedSenderMessageContent.CONTENT_HINT_IMPLICIT);

  private static final Map<Integer, ContentHint> TYPE_MAP = new HashMap<>();
  static {
    for (ContentHint value : values()) {
      TYPE_MAP.put(value.getType(), value);
    }
  }

  private final int type;

  ContentHint(int type) {
    this.type = type;
  }

  public int getType() {
    return type;
  }

  public static ContentHint fromType(int type) {
    return TYPE_MAP.getOrDefault(type, DEFAULT);
  }
}
