package org.thoughtcrime.securesms.conversation.colors;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A serializable set of color constants that can be used for avatars.
 */
public enum AvatarColor {
  C000("C000", 0xFFD00B0B),
  C010("C010", 0xFFC72A0A),
  C020("C020", 0xFFB34209),
  C030("C030", 0xFF9C5711),
  C040("C040", 0xFF866118),
  C050("C050", 0xFF76681E),
  C060("C060", 0xFF6C6C13),
  C070("C070", 0xFF5E6E0C),
  C080("C080", 0xFF507406),
  C090("C090", 0xFF3D7406),
  C100("C100", 0xFF2D7906),
  C110("C110", 0xFF1A7906),
  C120("C120", 0xFF067906),
  C130("C130", 0xFF067919),
  C140("C140", 0xFF06792D),
  C150("C150", 0xFF067940),
  C160("C160", 0xFF067953),
  C170("C170", 0xFF067462),
  C180("C180", 0xFF067474),
  C190("C190", 0xFF077288),
  C200("C200", 0xFF086DA0),
  C210("C210", 0xFF0A69C7),
  C220("C220", 0xFF0D59F2),
  C230("C230", 0xFF3454F4),
  C240("C240", 0xFF5151F6),
  C250("C250", 0xFF6447F5),
  C260("C260", 0xFF7A3DF5),
  C270("C270", 0xFF8F2AF4),
  C280("C280", 0xFFA20CED),
  C290("C290", 0xFFAF0BD0),
  C300("C300", 0xFFB80AB8),
  C310("C310", 0xFFC20AA3),
  C320("C320", 0xFFC70A88),
  C330("C330", 0xFFCB0B6B),
  C340("C340", 0xFFD00B4D),
  C350("C350", 0xFFD00B2C),
  CRIMSON("crimson", ChatColorsPalette.Bubbles.CRIMSON.asSingleColor()),
  VERMILLION("vermillion", ChatColorsPalette.Bubbles.VERMILION.asSingleColor()),
  BURLAP("burlap", ChatColorsPalette.Bubbles.BURLAP.asSingleColor()),
  FOREST("forest", ChatColorsPalette.Bubbles.FOREST.asSingleColor()),
  WINTERGREEN("wintergreen", ChatColorsPalette.Bubbles.WINTERGREEN.asSingleColor()),
  TEAL("teal", ChatColorsPalette.Bubbles.TEAL.asSingleColor()),
  BLUE("blue", ChatColorsPalette.Bubbles.BLUE.asSingleColor()),
  INDIGO("indigo", ChatColorsPalette.Bubbles.INDIGO.asSingleColor()),
  VIOLET("violet", ChatColorsPalette.Bubbles.VIOLET.asSingleColor()),
  PLUM("plum", ChatColorsPalette.Bubbles.PLUM.asSingleColor()),
  TAUPE("taupe", ChatColorsPalette.Bubbles.TAUPE.asSingleColor()),
  STEEL("steel", ChatColorsPalette.Bubbles.STEEL.asSingleColor()),
  ULTRAMARINE("ultramarine", ChatColorsPalette.Bubbles.ULTRAMARINE.asSingleColor()),
  UNKNOWN("unknown", ChatColorsPalette.Bubbles.STEEL.asSingleColor());

  /** Fast map of name to enum, while also giving us a location to map old colors to new ones. */
  private static final Map<String, AvatarColor> NAME_MAP = new HashMap<>();
  static {
    for (AvatarColor color : AvatarColor.values()) {
      NAME_MAP.put(color.serialize(), color);
    }

    NAME_MAP.put("red", CRIMSON);
    NAME_MAP.put("orange", VERMILLION);
    NAME_MAP.put("deep_orange", VERMILLION);
    NAME_MAP.put("brown", BURLAP);
    NAME_MAP.put("green", FOREST);
    NAME_MAP.put("light_green", WINTERGREEN);
    NAME_MAP.put("teal", TEAL);
    NAME_MAP.put("blue", BLUE);
    NAME_MAP.put("indigo", INDIGO);
    NAME_MAP.put("purple", VIOLET);
    NAME_MAP.put("deep_purple", VIOLET);
    NAME_MAP.put("pink", PLUM);
    NAME_MAP.put("blue_grey", TAUPE);
    NAME_MAP.put("grey", STEEL);
    NAME_MAP.put("ultramarine", ULTRAMARINE);
  }

  /** Colors that can be assigned via {@link #random()}. */
  private static final AvatarColor[] RANDOM_OPTIONS = new AvatarColor[] {
      C000,
      C010,
      C020,
      C030,
      C040,
      C050,
      C060,
      C070,
      C080,
      C090,
      C100,
      C110,
      C120,
      C130,
      C140,
      C150,
      C160,
      C170,
      C180,
      C190,
      C200,
      C210,
      C220,
      C230,
      C240,
      C250,
      C260,
      C270,
      C280,
      C290,
      C300,
      C310,
      C320,
      C330,
      C340,
      C350,
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

  public static @NonNull AvatarColor deserialize(@NonNull String name) {
    return Objects.requireNonNull(NAME_MAP.getOrDefault(name, C000));
  }
}
