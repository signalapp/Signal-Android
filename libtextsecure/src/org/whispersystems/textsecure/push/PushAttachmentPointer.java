package org.whispersystems.textsecure.push;

import android.os.Parcel;
import android.os.Parcelable;

public class PushAttachmentPointer implements Parcelable {

  public static final Parcelable.Creator<PushAttachmentPointer> CREATOR = new Parcelable.Creator<PushAttachmentPointer>() {
    @Override
    public PushAttachmentPointer createFromParcel(Parcel in) {
      return new PushAttachmentPointer(in);
    }

    @Override
    public PushAttachmentPointer[] newArray(int size) {
      return new PushAttachmentPointer[size];
    }
  };

  private final String contentType;
  private final long   id;
  private final byte[] key;

  public PushAttachmentPointer(String contentType, long id, byte[] key) {
    this.contentType = contentType;
    this.id          = id;
    this.key         = key;
  }

  public PushAttachmentPointer(Parcel in) {
    this.contentType = in.readString();
    this.id          = in.readLong();

    int keyLength = in.readInt();
    this.key      = new byte[keyLength];
    in.readByteArray(this.key);
  }

  public String getContentType() {
    return contentType;
  }

  public long getId() {
    return id;
  }

  public byte[] getKey() {
    return key;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(contentType);
    dest.writeLong(id);
    dest.writeInt(this.key.length);
    dest.writeByteArray(this.key);
  }
}
