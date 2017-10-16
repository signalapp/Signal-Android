package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.content.res.AppCompatResources;

import com.amulyakhare.textdrawable.TextDrawable;

import org.thoughtcrime.securesms.R;

import java.util.regex.Pattern;

public class GeneratedContactPhoto implements FallbackContactPhoto {

  private static final Pattern PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}\\p{P}\\p{S}]+");

  private final String name;

  public GeneratedContactPhoto(@NonNull String name) {
    this.name  = name;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    int targetSize = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    return TextDrawable.builder()
                       .beginConfig()
                       .width(targetSize)
                       .height(targetSize)
                       .textColor(inverted ? color : Color.WHITE)
                       .endConfig()
                       .buildRound(getCharacter(name), inverted ? Color.WHITE : color);
  }

  private String getCharacter(String name) {
    String cleanedName = PATTERN.matcher(name).replaceFirst("");

    if (cleanedName.isEmpty()) {
      return "#";
    } else {
      return new StringBuilder().appendCodePoint(cleanedName.codePointAt(0)).toString();
    }
  }

  @Override
  public Drawable asCallCard(Context context) {
    return AppCompatResources.getDrawable(context, R.drawable.ic_person_large);

  }
}
