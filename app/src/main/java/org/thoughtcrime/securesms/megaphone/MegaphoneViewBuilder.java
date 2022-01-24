package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MegaphoneViewBuilder {

  public static @Nullable View build(@NonNull Context context,
                                     @NonNull Megaphone megaphone,
                                     @NonNull MegaphoneActionController listener)
  {
    switch (megaphone.getStyle()) {
      case BASIC:
        return buildBasicMegaphone(context, megaphone, listener);
      case FULLSCREEN:
        return null;
      case ONBOARDING:
        return buildOnboardingMegaphone(context, megaphone, listener);
      case POPUP:
        return buildPopupMegaphone(context, megaphone, listener);
      default:
        throw new IllegalArgumentException("No view implemented for style!");
    }
  }

  private static @NonNull View buildBasicMegaphone(@NonNull Context context,
                                                   @NonNull Megaphone megaphone,
                                                   @NonNull MegaphoneActionController listener)
  {
    BasicMegaphoneView view = new BasicMegaphoneView(context);
    view.present(megaphone, listener);
    return view;
  }

  private static @NonNull View buildOnboardingMegaphone(@NonNull Context context,
                                                        @NonNull Megaphone megaphone,
                                                        @NonNull MegaphoneActionController listener)
  {
    OnboardingMegaphoneView view = new OnboardingMegaphoneView(context);
    view.present(megaphone, listener);
    return view;
  }

  private static @NonNull View buildPopupMegaphone(@NonNull Context context,
                                                   @NonNull Megaphone megaphone,
                                                   @NonNull MegaphoneActionController listener)
  {
    PopupMegaphoneView view = new PopupMegaphoneView(context);
    view.present(megaphone, listener);
    return view;
  }
}
