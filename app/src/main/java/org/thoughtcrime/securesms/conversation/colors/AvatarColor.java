package org.thoughtcrime.securesms.conversation.colors;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A serializable set of color constants that can be used for avatars.
 */
public enum AvatarColor {
  A100("A100", 0xFFE3E3FE),
  A110("A110", 0xFFDDE7FC),
  A120("A120", 0xFFD8E8F0),
  A130("A130", 0xFFCDE4CD),
  A140("A140", 0xFFEAE0F8),
  A150("A150", 0xFFF5E3FE),
  A160("A160", 0xFFF6D8EC),
  A170("A170", 0xFFF5D7D7),
  A180("A180", 0xFFFEF5D0),
  A190("A190", 0xFFEAE6D5),
  A200("A200", 0xFFD2D2DC),
  A210("A210", 0xFFD7D7D9),
  UNKNOWN("UNKNOWN", 0x00000000),
  ON_SURFACE_VARIANT("ON_SURFACE_VARIANT", 0x00000000);

  /**
   * Fast map of name to enum, while also giving us a location to map old colors to new ones.
   */
  private static final Map<String, AvatarColor> NAME_MAP = new HashMap<>();

  static {
    for (AvatarColor color : AvatarColor.values()) {
      NAME_MAP.put(color.serialize(), color);
    }

    NAME_MAP.put("C020", A170);
    NAME_MAP.put("C030", A170);
    NAME_MAP.put("C040", A180);
    NAME_MAP.put("C050", A180);
    NAME_MAP.put("C000", A190);
    NAME_MAP.put("C060", A190);
    NAME_MAP.put("C070", A190);
    NAME_MAP.put("C080", A130);
    NAME_MAP.put("C090", A130);
    NAME_MAP.put("C100", A130);
    NAME_MAP.put("C110", A130);
    NAME_MAP.put("C120", A130);
    NAME_MAP.put("C130", A130);
    NAME_MAP.put("C140", A130);
    NAME_MAP.put("C150", A130);
    NAME_MAP.put("C160", A130);
    NAME_MAP.put("C170", A120);
    NAME_MAP.put("C180", A120);
    NAME_MAP.put("C190", A120);
    NAME_MAP.put("C200", A110);
    NAME_MAP.put("C210", A110);
    NAME_MAP.put("C220", A110);
    NAME_MAP.put("C230", A100);
    NAME_MAP.put("C240", A100);
    NAME_MAP.put("C250", A100);
    NAME_MAP.put("C260", A100);
    NAME_MAP.put("C270", A140);
    NAME_MAP.put("C280", A140);
    NAME_MAP.put("C290", A140);
    NAME_MAP.put("C300", A150);
    NAME_MAP.put("C010", A170);
    NAME_MAP.put("C310", A150);
    NAME_MAP.put("C320", A150);
    NAME_MAP.put("C330", A160);
    NAME_MAP.put("C340", A160);
    NAME_MAP.put("C350", A160);
    NAME_MAP.put("crimson", A170);
    NAME_MAP.put("vermillion", A170);
    NAME_MAP.put("burlap", A190);
    NAME_MAP.put("forest", A130);
    NAME_MAP.put("wintergreen", A130);
    NAME_MAP.put("teal", A120);
    NAME_MAP.put("blue", A110);
    NAME_MAP.put("indigo", A100);
    NAME_MAP.put("violet", A140);
    NAME_MAP.put("plum", A150);
    NAME_MAP.put("taupe", A190);
    NAME_MAP.put("steel", A210);
    NAME_MAP.put("ultramarine", A100);
    NAME_MAP.put("unknown", A210);
    NAME_MAP.put("red", A170);
    NAME_MAP.put("orange", A170);
    NAME_MAP.put("deep_orange", A170);
    NAME_MAP.put("brown", A190);
    NAME_MAP.put("green", A130);
    NAME_MAP.put("light_green", A130);
    NAME_MAP.put("purple", A140);
    NAME_MAP.put("deep_purple", A140);
    NAME_MAP.put("pink", A150);
    NAME_MAP.put("blue_grey", A190);
    NAME_MAP.put("grey", A210);
  }

  /**
   * Colors that can be assigned via {@link #random()}.
   */
  static final AvatarColor[] RANDOM_OPTIONS = new AvatarColor[] {
      A100,
      A110,
      A120,
      A130,
      A140,
      A150,
      A160,
      A170,
      A180,
      A190,
      A200,
      A210
  };

  private final String name;
  private final int    color;

  AvatarColor(@NonNull String name, @ColorInt int color) {
    this.name  = name;
    this.color = color;
  }

  public @ColorInt int colorInt() {
    return color;
  }

  public static @NonNull AvatarColor random() {
    int position = (int) Math.floor(Math.random() * RANDOM_OPTIONS.length);
    return RANDOM_OPTIONS[position];
  }

  public @NonNull String serialize() {
    return name;
  }

  public static @NonNull AvatarColor deserialize(@Nullable String name) {
    return Objects.requireNonNull(NAME_MAP.getOrDefault(name, A210));
  }

  public static @Nullable AvatarColor fromColor(@ColorInt int color) {
    return Arrays.stream(values())
                 .filter(c -> c.color == color)
                 .findFirst()
                 .orElse(null);
  }
}
