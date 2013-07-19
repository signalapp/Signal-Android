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
  private final String key;

  public PushAttachmentPointer(String contentType, String key) {
    this.contentType = contentType;
    this.key         = key;
  }

  public PushAttachmentPointer(Parcel in) {
    this.contentType = in.readString();
    this.key         = in.readString();
  }

  public String getContentType() {
    return contentType;
  }

  public String getKey() {
    return key;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(contentType);
    dest.writeString(key);
  }
}
