package org.thoughtcrime.securesms.database.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

public class Mention implements Comparable<Mention>, Parcelable {
  private final RecipientId recipientId;
  private final int         start;
  private final int         length;

  public Mention(@NonNull RecipientId recipientId, int start, int length) {
    this.recipientId = recipientId;
    this.start       = start;
    this.length      = length;
  }

  protected Mention(Parcel in) {
    recipientId = in.readParcelable(RecipientId.class.getClassLoader());
    start       = in.readInt();
    length      = in.readInt();
  }

  public @NonNull RecipientId getRecipientId() {
    return recipientId;
  }

  public int getStart() {
    return start;
  }

  public int getLength() {
    return length;
  }

  @Override
  public int compareTo(Mention other) {
    return Integer.compare(start, other.start);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipientId, start, length);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (this == object) {
      return true;
    }

    if (object == null || getClass() != object.getClass()) {
      return false;
    }

    Mention that = (Mention) object;
    return recipientId.equals(that.recipientId) && start == that.start && length == that.length;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(recipientId, flags);
    dest.writeInt(start);
    dest.writeInt(length);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<Mention> CREATOR = new Creator<Mention>() {
    @Override
    public Mention createFromParcel(Parcel in) {
      return new Mention(in);
    }

    @Override
    public Mention[] newArray(int size) {
      return new Mention[size];
    }
  };
}
