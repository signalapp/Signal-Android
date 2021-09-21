package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.SimpleColorFilter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.Avatar;
import org.thoughtcrime.securesms.avatar.AvatarRenderer;
import org.thoughtcrime.securesms.avatar.Avatars;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.NameUtil;

import java.util.Objects;

public class GeneratedContactPhoto implements FallbackContactPhoto {

  private final String name;
  private final int    fallbackResId;
  private final int    targetSize;

  public GeneratedContactPhoto(@NonNull String name, @DrawableRes int fallbackResId) {
    this(name, fallbackResId, -1);
  }

  public GeneratedContactPhoto(@NonNull String name, @DrawableRes int fallbackResId, int targetSize) {
    this.name          = name;
    this.fallbackResId = fallbackResId;
    this.targetSize    = targetSize;
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color) {
    return asDrawable(context, color,false);
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    int targetSize = this.targetSize > 0
                     ? this.targetSize
                     : context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    String character = NameUtil.getAbbreviation(name);

    if (!TextUtils.isEmpty(character)) {
      Avatars.ForegroundColor foregroundColor = Avatars.getForegroundColor(color);
      Avatar.Text             avatar          = new Avatar.Text(character, new Avatars.ColorPair(color, foregroundColor), Avatar.DatabaseId.DoNotPersist.INSTANCE);
      Drawable                foreground      = AvatarRenderer.createTextDrawable(context, avatar, inverted, targetSize, false);
      Drawable                background      = Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.circle_tintable));

      background.setColorFilter(new SimpleColorFilter(inverted ? foregroundColor.getColorInt() : color.colorInt()));

      return new LayerDrawable(new Drawable[] { background, foreground });
    }

    return newFallbackDrawable(context, color, inverted);
  }

  @Override
  public Drawable asSmallDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    return asDrawable(context, color, inverted);
  }

  protected @DrawableRes int getFallbackResId() {
    return fallbackResId;
  }

  protected Drawable newFallbackDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    return new ResourceContactPhoto(fallbackResId).asDrawable(context, color, inverted);
  }

  @Override
  public Drawable asCallCard(@NonNull Context context) {
    return AppCompatResources.getDrawable(context, R.drawable.ic_person_large);

  }
}
