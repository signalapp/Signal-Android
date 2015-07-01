package org.thoughtcrime.securesms.color;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MaterialColors {

  public static final MaterialColor GREY = new GreyMaterialColor();

  public static final MaterialColorList CONVERSATION_PALETTE = new MaterialColorList(new ArrayList<>(Arrays.asList(
    new RedMaterialColor(),
    new PinkMaterialColor(),
    new PurpleMaterialColor(),
    new DeepPurpleMaterialColor(),
    new IndigoMaterialColor(),
    new BlueMaterialColor(),
    new LightBlueMaterialColor(),
    new CyanMaterialColor(),
    new TealMaterialColor(),
    new GreenMaterialColor(),
    new LightGreenMaterialColor(),
    // Lime
    // Yellow
    // Amber
    new OrangeMaterialColor(),
    new DeepOrangeMaterialColor(),
    new BrownMaterialColor(),
    // Grey
    new BlueGreyMaterialColor()
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

    public @Nullable MaterialColor getByColor(int colorValue) {
      for (MaterialColor color : colors) {
        if (color.represents(colorValue)) {
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

