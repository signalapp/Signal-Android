package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.amulyakhare.textdrawable.TextDrawable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.regex.Pattern;

public class GeneratedContactPhoto implements FallbackContactPhoto {

  private static final Pattern  PATTERN  = Pattern.compile("[^\\p{L}\\p{Nd}\\p{S}]+");
  private static final Typeface TYPEFACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);

  private final String name;
  private final int    fallbackResId;
  private final int    targetSize;
  private final int    fontSize;

  public GeneratedContactPhoto(@NonNull String name, @DrawableRes int fallbackResId) {
    this(name, fallbackResId, -1, ViewUtil.dpToPx(24));
  }

  public GeneratedContactPhoto(@NonNull String name, @DrawableRes int fallbackResId, int targetSize, int fontSize) {
    this.name          = name;
    this.fallbackResId = fallbackResId;
    this.targetSize    = targetSize;
    this.fontSize      = fontSize;
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors) {
    return asDrawable(context, chatColors, false);
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted) {
    int targetSize = this.targetSize != -1
                     ? this.targetSize
                     : context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    String character = getAbbreviation(name);

    if (!TextUtils.isEmpty(character)) {
      Drawable background = chatColors.asCircle();

      Drawable base = TextDrawable.builder()
                                  .beginConfig()
                                  .width(targetSize)
                                  .height(targetSize)
                                  .useFont(TYPEFACE)
                                  .fontSize(fontSize)
                                  .textColor(inverted ? chatColors.asSingleColor() : Color.WHITE)
                                  .endConfig()
                                  .buildRound(character, inverted ? Color.WHITE : Color.TRANSPARENT);

      Drawable gradient = ContextUtil.requireDrawable(context, R.drawable.avatar_gradient);
      return new LayerDrawable(new Drawable[] { background, base, gradient });
    }

    return newFallbackDrawable(context, chatColors, inverted);
  }

  @Override
  public Drawable asSmallDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted) {
    return asDrawable(context, chatColors, inverted);
  }

  protected @DrawableRes int getFallbackResId() {
    return fallbackResId;
  }

  protected Drawable newFallbackDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted) {
    return new ResourceContactPhoto(fallbackResId).asDrawable(context, chatColors, inverted);
  }

  private @Nullable String getAbbreviation(String name) {
    String[]      parts   = name.split(" ");
    StringBuilder builder = new StringBuilder();
    int           count   = 0;

    for (int i = 0; i < parts.length && count < 2; i++) {
      String cleaned = PATTERN.matcher(parts[i]).replaceFirst("");
      if (!TextUtils.isEmpty(cleaned)) {
        builder.appendCodePoint(cleaned.codePointAt(0));
        count++;
      }
    }

    if (builder.length() == 0) {
      return null;
    } else {
      return builder.toString();
    }
  }

  @Override
  public Drawable asCallCard(Context context) {
    return AppCompatResources.getDrawable(context, R.drawable.ic_person_large);

  }
}
