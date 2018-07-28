package org.thoughtcrime.securesms.color;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MaterialColors {

  public static final MaterialColorList CONVERSATION_PALETTE = new MaterialColorList(new ArrayList<>(Arrays.asList(
    MaterialColor.ORANGE,
    MaterialColor.RED,
    MaterialColor.PINK,
    MaterialColor.PURPLE,
    MaterialColor.INDIGO,
    MaterialColor.BLUE,
    MaterialColor.GREEN,
    MaterialColor.TEAL,
    MaterialColor.CYAN
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

