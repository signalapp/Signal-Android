package org.thoughtcrime.securesms.wallpaper;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.mms.GlideApp;

final class UriChatWallpaper implements ChatWallpaper, Parcelable {

  private final Uri uri;

  UriChatWallpaper(@NonNull Uri uri) {
    this.uri = uri;
  }

  protected UriChatWallpaper(Parcel in) {
    uri = in.readParcelable(Uri.class.getClassLoader());
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(uri, flags);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<UriChatWallpaper> CREATOR = new Creator<UriChatWallpaper>() {
    @Override
    public UriChatWallpaper createFromParcel(Parcel in) {
      return new UriChatWallpaper(in);
    }

    @Override
    public UriChatWallpaper[] newArray(int size) {
      return new UriChatWallpaper[size];
    }
  };

  @Override
  public void loadInto(@NonNull ImageView imageView) {
    GlideApp.with(imageView)
            .load(uri)
            .into(imageView);
  }
}
