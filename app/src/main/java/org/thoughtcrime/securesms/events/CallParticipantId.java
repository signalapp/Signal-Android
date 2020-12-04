package org.thoughtcrime.securesms.events;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

/**
 * Allow system to identify a call participant by their device demux id and their
 * recipient id.
 */
public final class CallParticipantId {

  public static final long DEFAULT_ID = -1;

  private final long        demuxId;
  private final RecipientId recipientId;

  public CallParticipantId(@NonNull Recipient recipient) {
    this(DEFAULT_ID, recipient.getId());
  }

  public CallParticipantId(long demuxId, @NonNull RecipientId recipientId) {
    this.demuxId     = demuxId;
    this.recipientId = recipientId;
  }

  public long getDemuxId() {
    return demuxId;
  }

  public @NonNull RecipientId getRecipientId() {
    return recipientId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final CallParticipantId that = (CallParticipantId) o;
    return demuxId == that.demuxId &&
           recipientId.equals(that.recipientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(demuxId, recipientId);
  }
}
