package org.privatechats.securesms.color;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MaterialColors {

  public static final MaterialColorList CONVERSATION_PALETTE = new MaterialColorList(new ArrayList<>(Arrays.asList(
    MaterialColor.RED,
    MaterialColor.PINK,
    MaterialColor.PURPLE,

    MaterialColor.DEEP_PURPLE,
    MaterialColor.INDIGO,
    MaterialColor.BLUE,

    MaterialColor.LIGHT_BLUE,
    MaterialColor.CYAN,
    MaterialColor.TEAL,
    MaterialColor.GREEN,
    MaterialColor.LIGHT_GREEN,
    // Lime
    // Yellow
    // Amber
    MaterialColor.ORANGE,
    MaterialColor.DEEP_ORANGE,
//    MaterialColor.BROWN,
    MaterialColor.AMBER,
    // Grey
    MaterialColor.BLUE_GREY
  )));

  public static class MaterialColorList {

    private final List<MaterialColor> colors;

    private MaterialColorList(List<MaterialColor> colors) {
      this.colors = colors;
    }

    public MaterialColor get(int index) {
      return colors.get(index);
    }

    public int size() {
      return colors.size();
    }

    public @Nullable MaterialColor getByColor(Context context, int colorValue) {
      for (MaterialColor color : colors) {
        if (color.represents(context, colorValue)) {
          return color;
        }
      }

      return null;
    }

    public int[] asConversationColorArray(@NonNull Context context) {
      int[] results = new int[colors.size()];
      int   index   = 0;

      for (MaterialColor color : colors) {
        results[index++] = color.toConversationColor(context);
      }

      return results;
    }

  }


}

