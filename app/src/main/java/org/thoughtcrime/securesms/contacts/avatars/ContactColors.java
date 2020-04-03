package org.thoughtcrime.securesms.contacts.avatars;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.color.MaterialColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Colors that can be randomly assigned to a contact.
 */
public class ContactColors {

  public static final MaterialColor UNKNOWN_COLOR = MaterialColor.STEEL;

  private static final List<MaterialColor> CONVERSATION_PALETTE = new ArrayList<>(Arrays.asList(
      MaterialColor.PLUM,
      MaterialColor.CRIMSON,
      MaterialColor.VERMILLION,
      MaterialColor.VIOLET,
      MaterialColor.BLUE,
      MaterialColor.INDIGO,
      MaterialColor.FOREST,
      MaterialColor.WINTERGREEN,
      MaterialColor.TEAL,
      MaterialColor.BURLAP,
      MaterialColor.TAUPE,
      MaterialColor.ULTRAMARINE
  ));

  public static MaterialColor generateFor(@NonNull String name) {
    return CONVERSATION_PALETTE.get(Math.abs(name.hashCode()) % CONVERSATION_PALETTE.size());
  }
}
