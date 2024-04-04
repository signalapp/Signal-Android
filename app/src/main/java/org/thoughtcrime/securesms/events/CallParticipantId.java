package org.thoughtcrime.securesms.events;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

/**
 * Allow system to identify a call participant by their device demux id and their
 * recipient id.
 */
public final class CallParticipantId implements Parcelable {

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

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeLong(demuxId);
    dest.writeParcelable(recipientId, flags);
  }

  @Override
  public @NonNull String toString() {
    return "CallParticipantId(demuxId=" + demuxId + ", recipientId=" + recipientId + ')';
  }

  public static final Parcelable.Creator<CallParticipantId> CREATOR = new Parcelable.Creator<CallParticipantId>() {
    @Override
    public CallParticipantId createFromParcel(Parcel in) {
      return new CallParticipantId(
          in.readLong(),
          Objects.requireNonNull(
              ParcelCompat.readParcelable(in,
                                          RecipientId.class.getClassLoader(),
                                          RecipientId.class)
          )
      );
    }

    @Override
    public CallParticipantId[] newArray(int size) {
      return new CallParticipantId[size];
    }
  };
}
