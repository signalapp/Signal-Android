package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.providers.AvatarProvider;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class AvatarUtil {

  private static final String TAG = Log.tag(AvatarUtil.class);

  public static final int UNDEFINED_SIZE = -1;

  private AvatarUtil() {
  }

  public static void loadBlurredIconIntoImageView(@NonNull Recipient recipient, @NonNull AppCompatImageView target) {
    Context context = target.getContext();

    ContactPhoto photo;

    if (recipient.isSelf()) {
      photo = new ProfileContactPhoto(Recipient.self());
    } else if (recipient.getContactPhoto() == null) {
      target.setImageDrawable(null);
      target.setBackgroundColor(ContextCompat.getColor(target.getContext(), R.color.black));
      return;
    } else {
      photo = recipient.getContactPhoto();
    }

    Glide.with(target)
            .load(photo)
            .transform(new BlurTransformation(context, 0.25f, BlurTransformation.MAX_RADIUS), new CenterCrop())
            .into(new CustomViewTarget<View, Drawable>(target) {
              @Override
              public void onLoadFailed(@Nullable Drawable errorDrawable) {
                target.setImageDrawable(null);
                target.setBackgroundColor(ContextCompat.getColor(target.getContext(), R.color.black));
              }

              @Override
              public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                target.setImageDrawable(resource);
              }

              @Override
              protected void onResourceCleared(@Nullable Drawable placeholder) {
                target.setImageDrawable(placeholder);
              }
            });
  }

  public static void loadIconIntoImageView(@NonNull Recipient recipient, @NonNull ImageView target) {
    loadIconIntoImageView(recipient, target, -1);
  }

  public static void loadIconIntoImageView(@NonNull Recipient recipient, @NonNull ImageView target, int requestedSize) {
    Context context = target.getContext();

    requestCircle(Glide.with(context).asDrawable(), context, recipient, requestedSize).into(target);
  }

  public static Bitmap loadIconBitmapSquareNoCache(@NonNull Context context,
                                                   @NonNull Recipient recipient,
                                                   int width,
                                                   int height)
      throws ExecutionException, InterruptedException
  {
    return requestSquare(Glide.with(context).asBitmap(), context, recipient)
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .submit(width, height)
        .get();
  }

  @WorkerThread
  public static @NonNull IconCompat getIconCompat(@NonNull Context context, @NonNull Recipient recipient) {
    if (Build.VERSION.SDK_INT > 29) {
      return IconCompat.createWithContentUri(AvatarProvider.getContentUri(recipient.getId()));
    } else {
      return IconCompat.createWithBitmap(getBitmapForNotification(context, recipient, AdaptiveBitmapMetrics.getInnerWidth()));
    }
  }

  @WorkerThread
  public static Bitmap getBitmapForNotification(@NonNull Context context, @NonNull Recipient recipient) {
    return getBitmapForNotification(context, recipient, UNDEFINED_SIZE);
  }

  @WorkerThread
  public static @NonNull Bitmap getBitmapForNotification(@NonNull Context context, @NonNull Recipient recipient, int size) {
    ThreadUtil.assertNotMainThread();

    try {
      AvatarTarget   avatarTarget   = new AvatarTarget(size);
      RequestManager requestManager = Glide.with(context);

      requestCircle(requestManager.asBitmap(), context, recipient, size).into(avatarTarget);

      Bitmap bitmap = avatarTarget.await();
      return Objects.requireNonNullElseGet(bitmap, () -> DrawableUtil.toBitmap(getFallback(context, recipient, size), size, size));
    } catch (InterruptedException e) {
      return DrawableUtil.toBitmap(getFallback(context, recipient, size), size, size);
    }
  }

  private static <T> RequestBuilder<T> requestCircle(@NonNull RequestBuilder<T> requestBuilder, @NonNull Context context, @NonNull Recipient recipient, int targetSize) {
    return request(requestBuilder, context, recipient, targetSize, new CircleCrop());
  }

  private static <T> RequestBuilder<T> requestSquare(@NonNull RequestBuilder<T> requestBuilder, @NonNull Context context, @NonNull Recipient recipient) {
    return request(requestBuilder, context, recipient, UNDEFINED_SIZE, new CenterCrop());
  }

  private static <T> RequestBuilder<T> request(@NonNull RequestBuilder<T> requestBuilder, @NonNull Context context, @NonNull Recipient recipient, int targetSize, @Nullable BitmapTransformation transformation) {
    return request(requestBuilder, context, recipient, true, targetSize, transformation);
  }

  private static <T> RequestBuilder<T> request(@NonNull RequestBuilder<T> requestBuilder, @NonNull Context context, @NonNull Recipient recipient, boolean loadSelf, int targetSize, @Nullable BitmapTransformation transformation) {

    final ContactPhoto photo;
    if (Recipient.self().equals(recipient) && loadSelf) {
      photo = new ProfileContactPhoto(recipient);
    } else {
      photo = recipient.getContactPhoto();
    }

    final int size = targetSize == -1 ? AdaptiveBitmapMetrics.getInnerWidth() : targetSize;
    final RequestBuilder<T> request = requestBuilder.load(photo)
                                                .error(getFallback(context, recipient, size))
                                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                                .override(size);

    if (recipient.getShouldBlurAvatar()) {
      BlurTransformation blur = new BlurTransformation(context, 0.25f, BlurTransformation.MAX_RADIUS);
      if (transformation != null) {
        return request.transform(blur, transformation);
      } else {
        return request.transform(blur);
      }
    } else if (transformation != null) {
      return request.transform(transformation);
    } else {
      return request;
    }
  }

  private static Drawable getFallback(@NonNull Context context, @NonNull Recipient recipient, int targetSize) {
    FallbackAvatar fallbackAvatar = FallbackAvatar.forTextOrDefault(recipient.getDisplayName(context), recipient.getAvatarColor());

    Drawable avatar = new FallbackAvatarDrawable(context, fallbackAvatar).circleCrop();
    avatar.setBounds(0, 0, targetSize, targetSize);

    return avatar;
  }

  /**
   * Allows caller to synchronously await for a bitmap of an avatar.
   */
  private static class AvatarTarget extends CustomTarget<Bitmap> {

    private final CountDownLatch          countDownLatch = new CountDownLatch(1);
    private final AtomicReference<Bitmap> bitmap         = new AtomicReference<>();

    private final int size;

    private AvatarTarget(int size) {
      this.size = size == UNDEFINED_SIZE ? AdaptiveBitmapMetrics.getInnerWidth() : size;
    }

    public @Nullable Bitmap await() throws InterruptedException {
      if (countDownLatch.await(1, TimeUnit.SECONDS)) {
        return bitmap.get();
      } else {
        Log.w(TAG, "AvatarTarget#await: Failed to load avatar in time! Returning null");
        return null;
      }
    }

    @Override
    public void onDestroy() {
      Log.d(TAG, "AvatarTarget: onDestroy");
      super.onDestroy();
    }

    @Override
    public void onLoadStarted(@Nullable Drawable placeholder) {
      Log.d(TAG, "AvatarTarget: onLoadStarted");
      super.onLoadStarted(placeholder);
    }

    @Override
    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
      Log.d(TAG, "AvatarTarget: onResourceReady");
      bitmap.set(resource);
      countDownLatch.countDown();
    }

    @Override
    public void onLoadFailed(@Nullable Drawable errorDrawable) {
      Log.d(TAG, "AvatarTarget: onLoadFailed");
      if (errorDrawable == null) {
        throw new AssertionError("Expected an error drawable.");
      }

      Bitmap errorBitmap = DrawableUtil.toBitmap(errorDrawable, size, size);
      bitmap.set(errorBitmap);
      countDownLatch.countDown();
    }

    @Override
    public void onLoadCleared(@Nullable Drawable placeholder) {
      Log.d(TAG, "AvatarTarget: onLoadCleared");
      bitmap.set(null);
      countDownLatch.countDown();
    }
  }
}
