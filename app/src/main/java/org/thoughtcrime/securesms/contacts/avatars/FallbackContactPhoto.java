package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.conversation.colors.ChatColors;

public interface FallbackContactPhoto {
  Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors);
  Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted);
  Drawable asSmallDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted);
  Drawable asCallCard(@NonNull Context context);
}
