package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequest;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.concurrent.ExecutionException;

public final class AvatarUtil {

  private AvatarUtil() {
  }

  public static void loadIconIntoImageView(@NonNull Recipient recipient, @NonNull ImageView target) {
    Context  context  = target.getContext();

    request(GlideApp.with(context).asDrawable(), context, recipient).into(target);
  }

  @WorkerThread
  public static IconCompat getIconForNotification(@NonNull Context context, @NonNull Recipient recipient) {
    try {
      return IconCompat.createWithBitmap(request(GlideApp.with(context).asBitmap(), context, recipient).submit().get());
    } catch (ExecutionException | InterruptedException e) {
      return null;
    }
  }

  public static GlideRequest<Drawable> getSelfAvatarOrFallbackIcon(@NonNull Context context, @DrawableRes int fallbackIcon) {
    return GlideApp.with(context)
                   .asDrawable()
                   .load(new ProfileContactPhoto(Recipient.self(), Recipient.self().getProfileAvatar()))
                   .error(fallbackIcon)
                   .circleCrop()
                   .diskCacheStrategy(DiskCacheStrategy.ALL);
  }

  private static <T> GlideRequest<T> request(@NonNull GlideRequest<T> glideRequest, @NonNull Context context, @NonNull Recipient recipient) {
    return glideRequest.load(new ProfileContactPhoto(recipient, recipient.getProfileAvatar()))
                       .error(getFallback(context, recipient))
                       .circleCrop()
                       .diskCacheStrategy(DiskCacheStrategy.ALL);
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
