package org.thoughtcrime.securesms.sharing;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

public final class ShareContactAndThread implements Parcelable {
  private final RecipientId recipientId;
  private final long        threadId;
  private final boolean     forceSms;
  private final boolean     isStory;

  public ShareContactAndThread(@NonNull RecipientId recipientId, long threadId, boolean forceSms, boolean isStory) {
    this.recipientId = recipientId;
    this.threadId    = threadId;
    this.forceSms    = forceSms;
    this.isStory     = isStory;
  }

  protected ShareContactAndThread(@NonNull Parcel in) {
    recipientId = in.readParcelable(RecipientId.class.getClassLoader());
    threadId    = in.readLong();
    forceSms    = in.readByte() == 1;
    isStory     = in.readByte() == 1;
  }

  public @NonNull RecipientId getRecipientId() {
    return recipientId;
  }

  public long getThreadId() {
    return threadId;
  }

  public boolean isForceSms() {
    return forceSms;
  }

  public boolean isStory() {
    return isStory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ShareContactAndThread that = (ShareContactAndThread) o;
    return threadId == that.threadId &&
           forceSms == that.forceSms &&
           isStory == that.isStory &&
           recipientId.equals(that.recipientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipientId, threadId, forceSms, isStory);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(recipientId, flags);
    dest.writeLong(threadId);
    dest.writeByte((byte) (forceSms ? 1 : 0));
    dest.writeByte((byte) (isStory ? 1 : 0));
  }

  public static final Creator<ShareContactAndThread> CREATOR = new Creator<ShareContactAndThread>() {
    @Override
    public ShareContactAndThread createFromParcel(@NonNull Parcel in) {
      return new ShareContactAndThread(in);
    }

    @Override
    public ShareContactAndThread[] newArray(int size) {
      return new ShareContactAndThread[size];
    }
  };

}
