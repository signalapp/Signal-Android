package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackPhoto80dp;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequest;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.concurrent.ExecutionException;

public final class AvatarUtil {

  private static final String TAG = Log.tag(AvatarUtil.class);

  private AvatarUtil() {
  }

  public static void loadBlurredIconIntoViewBackground(@NonNull Recipient recipient, @NonNull View target) {
    loadBlurredIconIntoViewBackground(recipient, target, false);
  }

  public static void loadBlurredIconIntoViewBackground(@NonNull Recipient recipient, @NonNull View target, boolean useSelfProfileAvatar) {
    Context context = target.getContext();

    ContactPhoto photo;

    if (recipient.isSelf() && useSelfProfileAvatar) {
      photo = new ProfileContactPhoto(Recipient.self(), Recipient.self().getProfileAvatar());
    } else if (recipient.getContactPhoto() == null) {
      target.setBackgroundColor(ContextCompat.getColor(target.getContext(), R.color.black));
      return;
    } else {
      photo = recipient.getContactPhoto();
    }

    GlideApp.with(target)
            .load(photo)
            .transform(new CenterCrop(), new BlurTransformation(context, 0.25f, BlurTransformation.MAX_RADIUS))
            .into(new CustomViewTarget<View, Drawable>(target) {
              @Override
              public void onLoadFailed(@Nullable Drawable errorDrawable) {
                target.setBackgroundColor(ContextCompat.getColor(target.getContext(), R.color.black));
              }

              @Override
              public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                target.setBackground(resource);
              }

              @Override
              protected void onResourceCleared(@Nullable Drawable placeholder) {
                target.setBackground(placeholder);
              }
            });
  }

  public static void loadIconIntoImageView(@NonNull Recipient recipient, @NonNull ImageView target) {
    Context  context  = target.getContext();

    requestCircle(GlideApp.with(context).asDrawable(), context, recipient).into(target);
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
  public static IconCompat getIconForNotification(@NonNull Context context, @NonNull Recipient recipient) {
    try {
      return IconCompat.createWithBitmap(requestCircle(GlideApp.with(context).asBitmap(), context, recipient).submit().get());
    } catch (ExecutionException | InterruptedException e) {
      return null;
    }
  }

  @RequiresApi(ConversationUtil.CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  public static Icon getIconForShortcut(@NonNull Context context, @NonNull Recipient recipient) {
    try {
      return Icon.createWithAdaptiveBitmap(getShortcutInfoBitmap(context, recipient));
    } catch (ExecutionException | InterruptedException e) {
      return Icon.createWithAdaptiveBitmap(getFallbackForShortcut(context, recipient));
    }
  }

  @WorkerThread
  public static Bitmap getBitmapForNotification(@NonNull Context context, @NonNull Recipient recipient) {
    try {
      return requestCircle(GlideApp.with(context).asBitmap(), context, recipient).submit().get();
    } catch (ExecutionException | InterruptedException e) {
      return null;
    }
  }

  private static @NonNull Bitmap getShortcutInfoBitmap(@NonNull Context context, @NonNull Recipient recipient) throws ExecutionException, InterruptedException {
    return DrawableUtil.wrapBitmapForShortcutInfo(request(GlideApp.with(context).asBitmap(), context, recipient, false).circleCrop().submit().get());
  }

  private static <T> GlideRequest<T> requestCircle(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient) {
    return request(glideRequest, context, recipient).circleCrop();
  }

  private static <T> GlideRequest<T> requestSquare(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient) {
    return request(glideRequest, context, recipient).centerCrop();
  }

  private static <T> GlideRequest<T> request(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient) {
    return request(glideRequest, context, recipient, true);
  }

  private static <T> GlideRequest<T> request(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient, boolean loadSelf) {
    final ContactPhoto photo;
    if (Recipient.self().equals(recipient) && loadSelf) {
      photo = new ProfileContactPhoto(recipient, recipient.getProfileAvatar());
    } else {
      photo = recipient.getContactPhoto();
    }

    return glideRequest.load(photo)
                       .error(getFallback(context, recipient))
                       .diskCacheStrategy(DiskCacheStrategy.ALL);
  }

  private static @NonNull Bitmap getFallbackForShortcut(@NonNull Context context, @NonNull Recipient recipient) {
    @DrawableRes final int photoSource;
    if (recipient.isSelf()) {
      photoSource = R.drawable.ic_note_80;
    } else if (recipient.isGroup()) {
      photoSource = R.drawable.ic_group_80;
    } else {
      photoSource = R.drawable.ic_profile_80;
    }

    Bitmap toWrap  = DrawableUtil.toBitmap(new FallbackPhoto80dp(photoSource, recipient.getColor()).asDrawable(context, -1), ViewUtil.dpToPx(80), ViewUtil.dpToPx(80));
    Bitmap wrapped = DrawableUtil.wrapBitmapForShortcutInfo(toWrap);

    toWrap.recycle();

    return wrapped;
  }

  private static Drawable getFallback(@NonNull Context context, @NonNull Recipient recipient) {
    String        name          = Optional.fromNullable(recipient.getDisplayName(context)).or("");
    MaterialColor fallbackColor = recipient.getColor();

    if (fallbackColor == ContactColors.UNKNOWN_COLOR && !TextUtils.isEmpty(name)) {
      fallbackColor = ContactColors.generateFor(name);
    }

    return new GeneratedContactPhoto(name, R.drawable.ic_profile_outline_40).asDrawable(context, fallbackColor.toAvatarColor(context));
  }
}
