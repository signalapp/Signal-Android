package org.thoughtcrime.securesms.stickers;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

public class StickerLocator implements Parcelable {

  private final String packId;
  private final String packKey;
  private final int    stickerId;

  public StickerLocator(@NonNull String packId, @NonNull String packKey, int stickerId) {
    this.packId    = packId;
    this.packKey   = packKey;
    this.stickerId = stickerId;
  }

  private StickerLocator(Parcel in) {
    packId    = in.readString();
    packKey   = in.readString();
    stickerId = in.readInt();
  }

  public @NonNull String getPackId() {
    return packId;
  }

  public @NonNull String getPackKey() {
    return packKey;
  }

  public @NonNull int getStickerId() {
    return stickerId;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(packId);
    dest.writeString(packKey);
    dest.writeInt(stickerId);
  }

  public static final Creator<StickerLocator> CREATOR = new Creator<StickerLocator>() {
    @Override
    public StickerLocator createFromParcel(Parcel in) {
      return new StickerLocator(in);
    }

    @Override
    public StickerLocator[] newArray(int size) {
      return new StickerLocator[size];
    }
  };
}
