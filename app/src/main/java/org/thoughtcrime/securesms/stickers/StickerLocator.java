package org.thoughtcrime.securesms.stickers;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class StickerLocator implements Parcelable {

  private final String packId;
  private final String packKey;
  private final int    stickerId;
  private final String emoji;

  public StickerLocator(@NonNull String packId, @NonNull String packKey, int stickerId, @Nullable String emoji) {
    this.packId    = packId;
    this.packKey   = packKey;
    this.stickerId = stickerId;
    this.emoji     = emoji;
  }

  private StickerLocator(Parcel in) {
    packId    = in.readString();
    packKey   = in.readString();
    stickerId = in.readInt();
    emoji     = in.readString();
  }

  public @NonNull String getPackId() {
    return packId;
  }

  public @NonNull String getPackKey() {
    return packKey;
  }

  public int getStickerId() {
    return stickerId;
  }

  public @Nullable String getEmoji() {
    return emoji;
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
    dest.writeString(emoji);
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
