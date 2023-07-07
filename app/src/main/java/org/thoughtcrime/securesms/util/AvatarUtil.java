package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequest;
import org.thoughtcrime.securesms.providers.AvatarProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

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

    GlideApp.with(target)
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
    Context  context  = target.getContext();

    requestCircle(GlideApp.with(context).asDrawable(), context, recipient, requestedSize).into(target);
  }

  public static Bitmap loadIconBitmapSquareNoCache(@NonNull Context context,
                                                   @NonNull Recipient recipient,
                                                   int width,
                                                   int height)
      throws ExecutionException, InterruptedException
  {
    return requestSquare(GlideApp.with(context).asBitmap(), context, recipient)
                                 .skipMemoryCache(true)
                                 .diskCacheStrategy(DiskCacheStrategy.NONE)
                                 .submit(width, height)
                                 .get();
  }

  @WorkerThread
  public static IconCompat getIconWithUriForNotification(@NonNull Context context, @NonNull RecipientId recipientId) {
    return IconCompat.createWithContentUri(AvatarProvider.getContentUri(context, recipientId));
  }

  @WorkerThread
  public static @NonNull IconCompat getIconCompatForShortcut(@NonNull Context context, @NonNull Recipient recipient) {
    try {
      GlideRequest<Bitmap> glideRequest = GlideApp.with(context).asBitmap().load(new ConversationShortcutPhoto(recipient));
      if (recipient.shouldBlurAvatar()) {
        glideRequest = glideRequest.transform(new BlurTransformation(context, 0.25f, BlurTransformation.MAX_RADIUS));
      }
      return IconCompat.createWithAdaptiveBitmap(glideRequest.submit().get());
    } catch (ExecutionException | InterruptedException e) {
      Log.w(TAG, "Failed to generate shortcut icon for recipient " + recipient.getId() + ". Generating fallback.", e);

      Drawable fallbackDrawable = getFallback(context, recipient, DrawableUtil.SHORTCUT_INFO_WRAPPED_SIZE);
      Bitmap   fallbackBitmap   = DrawableUtil.toBitmap(fallbackDrawable, DrawableUtil.SHORTCUT_INFO_WRAPPED_SIZE, DrawableUtil.SHORTCUT_INFO_WRAPPED_SIZE);
      Bitmap   wrappedBitmap    = DrawableUtil.wrapBitmapForShortcutInfo(fallbackBitmap);

      return IconCompat.createWithAdaptiveBitmap(wrappedBitmap);
    }
  }

  @WorkerThread
  public static Bitmap getBitmapForNotification(@NonNull Context context, @NonNull Recipient recipient) {
    return getBitmapForNotification(context, recipient, UNDEFINED_SIZE);
  }

  @WorkerThread
  public static Bitmap getBitmapForNotification(@NonNull Context context, @NonNull Recipient recipient, int size) {
    try {
      return requestCircle(GlideApp.with(context).asBitmap(), context, recipient, size).submit().get();
    } catch (ExecutionException | InterruptedException e) {
      return null;
    }
  }

  private static <T> GlideRequest<T> requestCircle(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient, int targetSize) {
    return request(glideRequest, context, recipient, targetSize, new CircleCrop());
  }

  private static <T> GlideRequest<T> requestSquare(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient) {
    return request(glideRequest, context, recipient, UNDEFINED_SIZE, new CenterCrop());
  }

  private static <T> GlideRequest<T> request(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient, int targetSize, @Nullable BitmapTransformation transformation) {
    return request(glideRequest, context, recipient, true, targetSize, transformation);
  }

  private static <T> GlideRequest<T> request(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient, boolean loadSelf, int targetSize, @Nullable BitmapTransformation transformation) {
    final ContactPhoto photo;
    if (Recipient.self().equals(recipient) && loadSelf) {
      photo = new ProfileContactPhoto(recipient);
    } else {
      photo = recipient.getContactPhoto();
    }

    final GlideRequest<T> request = glideRequest.load(photo)
                                                .error(getFallback(context, recipient, targetSize))
                                                .diskCacheStrategy(DiskCacheStrategy.ALL);

    if (recipient.shouldBlurAvatar()) {
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
    String name = Optional.ofNullable(recipient.getDisplayName(context)).orElse("");

    return new GeneratedContactPhoto(name, R.drawable.ic_profile_outline_40, targetSize).asDrawable(context, recipient.getAvatarColor());
  }
}
