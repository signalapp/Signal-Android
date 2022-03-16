package org.thoughtcrime.securesms.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ByteUnit;
import org.thoughtcrime.securesms.util.LRUCache;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class UriChatWallpaper implements ChatWallpaper, Parcelable {

  private static final LruCache<Uri, Bitmap> CACHE = new LruCache<Uri, Bitmap>((int) Runtime.getRuntime().maxMemory() / 8) {
    @Override
    protected int sizeOf(Uri key, Bitmap value) {
      return value.getByteCount();
    }
  };

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
    Bitmap cached = CACHE.get(uri);
    if (cached != null) {
      Log.d(TAG, "Using cached value.");
      imageView.setImageBitmap(CACHE.get(uri));
    } else {
      Log.d(TAG, "Not in cache. Fetching using Glide.");
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
  }

  @Override
  public boolean prefetch(@NonNull Context context, long maxWaitTime) {
    Bitmap cached = CACHE.get(uri);
    if (cached != null) {
      Log.d(TAG, "Already cached, skipping prefetch.");
      return true;
    }

    long startTime = System.currentTimeMillis();
    try {
      Bitmap bitmap = GlideApp.with(context)
                              .asBitmap()
                              .load(new DecryptableStreamUriLoader.DecryptableUri(uri))
                              .submit()
                              .get(maxWaitTime, TimeUnit.MILLISECONDS);

      CACHE.put(uri, bitmap);
      Log.d(TAG, "Prefetched wallpaper in " + (System.currentTimeMillis() - startTime) + " ms.");

      return true;
    } catch (ExecutionException | InterruptedException e) {
      Log.w(TAG, "Failed to prefetch wallpaper.", e);
    } catch (TimeoutException e) {
      Log.w(TAG, "Timed out waiting for prefetch.");
    }

    return false;
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
