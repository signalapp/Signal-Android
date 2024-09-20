package org.thoughtcrime.securesms.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class UriChatWallpaper implements ChatWallpaper, Parcelable {

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
    if (cached != null && !cached.isRecycled()) {
      Log.d(TAG, "Using cached value.");
      imageView.setImageBitmap(cached);
    } else {
      Log.d(TAG, "Not in cache or recycled. Fetching using Glide.");
      Glide.with(imageView.getContext().getApplicationContext())
              .asBitmap()
              .load(new DecryptableStreamUriLoader.DecryptableUri(uri))
              .skipMemoryCache(true)
              .diskCacheStrategy(DiskCacheStrategy.NONE)
              .addListener(new RequestListener<>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                  Log.w(TAG, "Failed to load wallpaper " + uri);
                  return false;
                }

                @Override
                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                  Log.i(TAG, "Loaded wallpaper " + uri + " on " + Thread.currentThread().getName());
                  CACHE.put(uri, resource);
                  return false;
                }
              })
              .into(imageView);
    }
  }

  @Override
  public boolean prefetch(@NonNull Context context, long maxWaitTime) {
    Bitmap cached = CACHE.get(uri);
    if (cached != null && !cached.isRecycled()) {
      Log.d(TAG, "Already cached, skipping prefetch.");
      return true;
    }

    long startTime = System.currentTimeMillis();
    try {
      Bitmap bitmap = Glide.with(context.getApplicationContext())
                              .asBitmap()
                              .load(new DecryptableStreamUriLoader.DecryptableUri(uri))
                              .skipMemoryCache(true)
                              .diskCacheStrategy(DiskCacheStrategy.NONE)
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

  public @NonNull Uri getUri() {
    return uri;
  }

  @Override
  public @NonNull Wallpaper serialize() {
    return new Wallpaper.Builder()
                        .file_(new Wallpaper.File.Builder().uri(uri.toString()).build())
                        .dimLevelInDarkTheme(dimLevelInDarkTheme)
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
