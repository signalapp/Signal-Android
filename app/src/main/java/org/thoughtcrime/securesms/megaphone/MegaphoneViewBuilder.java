package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;

public class MegaphoneViewBuilder {

  public static @Nullable View build(@NonNull Context context,
                                     @NonNull Megaphone megaphone,
                                     @NonNull MegaphoneActionController listener)
  {
    switch (megaphone.getStyle()) {
      case BASIC:
      case ONBOARDING:
      case POPUP:
        ComposeView composeView = new ComposeView(context);
        MegaphoneComponentKt.setContent(composeView, megaphone, listener);
        return composeView;
      case FULLSCREEN:
        return null;
      default:
        throw new IllegalArgumentException("No view implemented for style!");
    }
  }
}
