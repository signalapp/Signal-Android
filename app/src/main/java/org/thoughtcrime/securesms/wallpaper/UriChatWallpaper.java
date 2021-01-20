package org.thoughtcrime.securesms.wallpaper;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;
import org.thoughtcrime.securesms.mms.GlideApp;

final class UriChatWallpaper implements ChatWallpaper, Parcelable {

  private final Uri uri;

  public UriChatWallpaper(@NonNull Uri uri) {
    this.uri = uri;
  }

  @Override
  public void loadInto(@NonNull ImageView imageView) {
    GlideApp.with(imageView)
            .load(uri)
            .into(imageView);
  }

  @Override
  public @NonNull Wallpaper serialize() {
    return Wallpaper.newBuilder()
                    .setFile(Wallpaper.File.newBuilder().setUri(uri.toString()))
                    .build();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(uri.toString());
  }

  public static final Creator<UriChatWallpaper> CREATOR = new Creator<UriChatWallpaper>() {
    @Override
    public UriChatWallpaper createFromParcel(Parcel in) {
      return new UriChatWallpaper(Uri.parse(in.readString()));
    }

    @Override
    public UriChatWallpaper[] newArray(int size) {
      return new UriChatWallpaper[size];
    }
  };
}
