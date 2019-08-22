package org.thoughtcrime.securesms.color;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;

import java.util.HashMap;
import java.util.Map;

import static org.thoughtcrime.securesms.util.ThemeUtil.isDarkTheme;

public enum MaterialColor {
  CRIMSON    (R.color.conversation_crimson,     R.color.conversation_crimson_tint,     R.color.conversation_crimson_shade,     "red"),
  VERMILLION (R.color.conversation_vermillion,  R.color.conversation_vermillion_tint,  R.color.conversation_vermillion_shade,  "orange"),
  BURLAP     (R.color.conversation_burlap,      R.color.conversation_burlap_tint,      R.color.conversation_burlap_shade,      "brown"),
  FOREST     (R.color.conversation_forest,      R.color.conversation_forest_tint,      R.color.conversation_forest_shade,      "green"),
  WINTERGREEN(R.color.conversation_wintergreen, R.color.conversation_wintergreen_tint, R.color.conversation_wintergreen_shade, "light_green"),
  TEAL       (R.color.conversation_teal,        R.color.conversation_teal_tint,        R.color.conversation_teal_shade,        "teal"),
  BLUE       (R.color.conversation_blue,        R.color.conversation_blue_tint,        R.color.conversation_blue_shade,        "blue"),
  INDIGO     (R.color.conversation_indigo,      R.color.conversation_indigo_tint,      R.color.conversation_indigo_shade,      "indigo"),
  VIOLET     (R.color.conversation_violet,      R.color.conversation_violet_tint,      R.color.conversation_violet_shade,      "purple"),
  PLUM       (R.color.conversation_plumb,       R.color.conversation_plumb_tint,       R.color.conversation_plumb_shade,       "pink"),
  TAUPE      (R.color.conversation_taupe,       R.color.conversation_taupe_tint,       R.color.conversation_taupe_shade,       "blue_grey"),
  STEEL      (R.color.conversation_steel,       R.color.conversation_steel_tint,       R.color.conversation_steel_shade,       "grey"),
  GROUP      (R.color.conversation_group,       R.color.conversation_group_tint,       R.color.conversation_group_shade,       "blue");

  private static final Map<String, MaterialColor> COLOR_MATCHES = new HashMap<String, MaterialColor>() {{
    put("red",         CRIMSON);
    put("deep_orange", CRIMSON);
    put("orange",      VERMILLION);
    put("amber",       VERMILLION);
    put("brown",       BURLAP);
    put("yellow",      BURLAP);
    put("pink",        PLUM);
    put("purple",      VIOLET);
    put("deep_purple", VIOLET);
    put("indigo",      INDIGO);
    put("blue",        BLUE);
    put("light_blue",  BLUE);
    put("cyan",        TEAL);
    put("teal",        TEAL);
    put("green",       FOREST);
    put("light_green", WINTERGREEN);
    put("lime",        WINTERGREEN);
    put("blue_grey",   TAUPE);
    put("grey",        STEEL);
    put("group_color", GROUP);
  }};

  private final @ColorRes int mainColor;
  private final @ColorRes int tintColor;
  private final @ColorRes int shadeColor;

  private final String serialized;


  MaterialColor(@ColorRes int mainColor, @ColorRes int tintColor, @ColorRes int shadeColor, String serialized) {
    this.mainColor  = mainColor;
    this.tintColor  = tintColor;
    this.shadeColor = shadeColor;
    this.serialized = serialized;
  }

  public @ColorInt int toConversationColor(@NonNull Context context) {
    return context.getResources().getColor(mainColor);
  }

  public @ColorInt int toAvatarColor(@NonNull Context context) {
    return context.getResources().getColor(isDarkTheme(context) ? shadeColor : mainColor);
  }

  public @ColorInt int toActionBarColor(@NonNull Context context) {
    return context.getResources().getColor(mainColor);
  }

  public @ColorInt int toStatusBarColor(@NonNull Context context) {
    return context.getResources().getColor(shadeColor);
  }

  public @ColorRes int toQuoteBarColorResource(@NonNull Context context, boolean outgoing) {
    if (outgoing) {
      return isDarkTheme(context) ? tintColor : shadeColor ;
    }
    return R.color.core_white;
  }

  public @ColorInt int toQuoteBackgroundColor(@NonNull Context context, boolean outgoing) {
    if (outgoing) {
      int color = toConversationColor(context);
      int alpha = isDarkTheme(context) ? (int) (0.2 * 255) : (int) (0.4 * 255);
      return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    return context.getResources().getColor(isDarkTheme(context) ? R.color.transparent_black_70
                                                                : R.color.transparent_white_aa);
  }

  public @ColorInt int toQuoteFooterColor(@NonNull Context context, boolean outgoing) {
    if (outgoing) {
      int color = toConversationColor(context);
      int alpha = isDarkTheme(context) ? (int) (0.4 * 255) : (int) (0.6 * 255);
      return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    return context.getResources().getColor(isDarkTheme(context) ? R.color.transparent_black_90
                                                                : R.color.transparent_white_bb);
  }

  public boolean represents(Context context, int colorValue) {
    return context.getResources().getColor(mainColor)  == colorValue ||
           context.getResources().getColor(tintColor)  == colorValue ||
           context.getResources().getColor(shadeColor) == colorValue;
  }

  public String serialize() {
    return serialized;
  }

  public static MaterialColor fromSerialized(String serialized) throws UnknownColorException {
    if (COLOR_MATCHES.containsKey(serialized)) {
      return COLOR_MATCHES.get(serialized);
    }

    throw new UnknownColorException("Unknown color: " + serialized);
  }

  public static class UnknownColorException extends Exception {
    public UnknownColorException(String message) {
      super(message);
    }
  }
}
