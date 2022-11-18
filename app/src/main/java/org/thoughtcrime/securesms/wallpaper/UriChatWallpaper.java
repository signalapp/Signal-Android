package org.thoughtcrime.securesms.wallpaper;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.jetbrains.annotations.NotNull;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette;
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideApp;

import java.util.Objects;

final class UriChatWallpaper implements ChatWallpaper, Parcelable {

  private static final String TAG = Log.tag(UriChatWallpaper.class);

  private final Uri   uri;
  private final float dimLevelInDarkTheme;

  public UriChatWallpaper(@NonNull Uri uri, float dimLevelInDarkTheme) {
    this.uri                 = uri;
    this.dimLevelInDarkTheme = dimLevelInDarkTheme;
  }

  @Override
  public float getDimLevelForDarkTheme() {
    return dimLevelInDarkTheme;
  }

  @Override
  public boolean isPhoto() {
    return true;
  }

  @Override
  public void loadInto(@NonNull ImageView imageView) {
    GlideApp.with(imageView)
            .load(new DecryptableStreamUriLoader.DecryptableUri(uri))
            .addListener(new RequestListener<Drawable>() {
              @Override
              public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                Log.w(TAG, "Failed to load wallpaper " + uri);
                return false;
              }

              @Override
              public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                Log.i(TAG, "Loaded wallpaper " + uri);
                return false;
              }
            })
            .into(imageView);
  }

  @Override
  public @NonNull Wallpaper serialize() {
    return Wallpaper.newBuilder()
                    .setFile(Wallpaper.File.newBuilder().setUri(uri.toString()))
                    .setDimLevelInDarkTheme(dimLevelInDarkTheme)
                    .build();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(uri.toString());
    dest.writeFloat(dimLevelInDarkTheme);
  }

  @Override
  public boolean isSameSource(@NonNull ChatWallpaper chatWallpaper) {
    if (this == chatWallpaper) return true;
    if (getClass() != chatWallpaper.getClass()) return false;
    UriChatWallpaper that = (UriChatWallpaper) chatWallpaper;

    return uri.equals(that.uri);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UriChatWallpaper that = (UriChatWallpaper) o;
    return Float.compare(that.dimLevelInDarkTheme, dimLevelInDarkTheme) == 0 &&
           uri.equals(that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, dimLevelInDarkTheme);
  }

  public static final Creator<UriChatWallpaper> CREATOR = new Creator<UriChatWallpaper>() {
    @Override
    public UriChatWallpaper createFromParcel(Parcel in) {
      return new UriChatWallpaper(Uri.parse(in.readString()), in.readFloat());
    }

    @Override
    public UriChatWallpaper[] newArray(int size) {
      return new UriChatWallpaper[size];
    }
  };
}
