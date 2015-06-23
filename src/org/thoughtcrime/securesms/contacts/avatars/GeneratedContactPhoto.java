package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.thoughtcrime.securesms.R;

public class GeneratedContactPhoto implements ContactPhoto {

  private final String name;

  GeneratedContactPhoto(@NonNull String name) {
    this.name  = name;
  }

  @Override
  public Drawable asDrawable(Context context, int background) {
    int targetSize = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    return TextDrawable.builder()
                       .beginConfig()
                       .width(targetSize)
                       .height(targetSize)
                       .endConfig()
                       .buildRound(String.valueOf(name.charAt(0)), background);
  }
}
