package org.thoughtcrime.securesms.contacts.avatars;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.color.MaterialColor;

/**
 * Used for migrating legacy colors to modern colors. For normal color generation, use
 * {@link ContactColors}.
 */
public class ContactColorsLegacy {

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
      return ContactColors.generateFor(name);
    }
  }
}
