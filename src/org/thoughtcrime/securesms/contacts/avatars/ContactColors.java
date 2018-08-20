package org.thoughtcrime.securesms.contacts.avatars;

import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.color.MaterialColors;

public class ContactColors {

  public static final MaterialColor UNKNOWN_COLOR = MaterialColor.GREY;

  private static final String[] LEGACY_PALETTE = new String[] {
      "red",
      "pink",
      "purple",
      "deep_purple",
      "indigo",
      "blue",
      "light_blue",
      "cyan",
      "teal",
      "green",
      "light_green",
      "orange",
      "deep_orange",
      "amber",
      "blue_grey"
  };

  public static MaterialColor generateFor(@NonNull String name) {
    String serialized = LEGACY_PALETTE[Math.abs(name.hashCode()) % LEGACY_PALETTE.length];
    try {
      return MaterialColor.fromSerialized(serialized);
    } catch (MaterialColor.UnknownColorException e) {
      return MaterialColors.CONVERSATION_PALETTE.get(Math.abs(name.hashCode()) % MaterialColors.CONVERSATION_PALETTE.size());
    }
  }

}
