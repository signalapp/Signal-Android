package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.makeramen.roundedimageview.RoundedDrawable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;

public class TransparentContactPhoto implements FallbackContactPhoto {

  public TransparentContactPhoto() {}

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors) {
    return asDrawable(context, chatColors, false);
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted) {
    return RoundedDrawable.fromDrawable(context.getResources().getDrawable(android.R.color.transparent));
  }

  @Override
  public Drawable asSmallDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted) {
    return asDrawable(context, chatColors, inverted);
  }

  @Override
  public Drawable asCallCard(@NonNull Context context) {
    return ContextCompat.getDrawable(context, R.drawable.ic_contact_picture_large);
  }

}
