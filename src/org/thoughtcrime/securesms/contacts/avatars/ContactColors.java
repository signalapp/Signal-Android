package org.thoughtcrime.securesms.contacts.avatars;

import android.support.annotation.NonNull;
import android.util.SparseIntArray;

import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContactColors {

  public static final  int UNKNOWN_COLOR   = 0xff9E9E9E;

  private static final int RED_300         = 0xffE57373;
  private static final int RED_500         = 0xffF44336;
  private static final int RED_700         = 0xFFD32F2F;
  private static final int PINK_300        = 0xffF06292;
  private static final int PINK_500        = 0xffE91E63;
  private static final int PINK_700        = 0xFFC2185B;
  private static final int PURPLE_300      = 0xffBA68C8;
  private static final int PURPLE_500      = 0Xff9C27B0;
  private static final int PURPLE_700      = 0xFF7B1FA2;
  private static final int DEEP_PURPLE_300 = 0xff9575CD;
  private static final int DEEP_PURPLE_500 = 0xff673AB7;
  private static final int DEEP_PURPLE_700 = 0xFF512DA8;
  private static final int INDIGO_300      = 0xff7986CB;
  private static final int INDIGO_500      = 0xff3F51B5;
  private static final int INDIGO_700      = 0xff303F9F;
  private static final int BLUE_300        = 0xff64B5F6;
  private static final int BLUE_500        = 0xff2196F3;
  private static final int BLUE_700        = 0xFF1976D2;
  private static final int LIGHT_BLUE_300  = 0xff4FC3F7;
  private static final int LIGHT_BLUE_500  = 0xff03A9F4;
  private static final int LIGHT_BLUE_700  = 0xFF0288D1;
  private static final int CYAN_300        = 0xff4DD0E1;
  private static final int CYAN_500        = 0xff00BCD4;
  private static final int CYAN_700        = 0xFF0097A7;
  private static final int TEAL_300        = 0xFF4DB6AC;
  private static final int TEAL_500        = 0xff009688;
  private static final int TEAL_700        = 0xFF00796B;
  private static final int GREEN_300       = 0xFF81C784;
  private static final int GREEN_500       = 0xff4CAF50;
  private static final int GREEN_700       = 0xFF388E3C;
  private static final int LIGHT_GREEN_300 = 0xFFAED581;
  private static final int LIGHT_GREEN_500 = 0xff8BC34A;
  private static final int LIGHT_GREEN_700 = 0xFF689F38;
  private static final int LIME_300        = 0xFFDCE775;
  private static final int LIME_500        = 0XFFCDDC39;
  private static final int LIME_700        = 0xFFAFB42B;
  private static final int YELLOW_300      = 0xFFFFF176;
  private static final int YELLOW_500      = 0xffFFEB3B;
  private static final int YELLOW_700      = 0xFFFBC02D;
  private static final int AMBER_300       = 0xFFFFD54F;
  private static final int AMBER_500       = 0XFFFFC107;
  private static final int AMBER_700       = 0xFFFFA000;
  private static final int ORANGE_300      = 0xFFFFB74D;
  private static final int ORANGE_500      = 0xffFF9800;
  private static final int ORANGE_700      = 0xFFF57C00;
  private static final int DEEP_ORANGE_300 = 0xFFFF8A65;
  private static final int DEEP_ORANGE_500 = 0xffFF5722;
  private static final int DEEP_ORANGE_700 = 0xFFE64A19;
  private static final int BROWN_300       = 0xFFA1887F;
  private static final int BROWN_500       = 0xff795548;
  private static final int BROWN_700       = 0xFF5D4037;
  private static final int BLUE_GREY_300   = 0xFF90A4AE;
  private static final int BLUE_GREY_500   = 0xff607D8B;
  private static final int BLUE_GREY_700   = 0xFF455A64;

  private static final List<Integer> MATERIAL_300 = new ArrayList<>(Arrays.asList(
      RED_300,
      PINK_300,
      PURPLE_300,
      DEEP_PURPLE_300,
      INDIGO_300,
      BLUE_300,
      LIGHT_BLUE_300,
      CYAN_300,
      TEAL_300,
      GREEN_300,
      LIGHT_GREEN_300,
      LIME_300,
      AMBER_300,
      ORANGE_300,
      DEEP_ORANGE_300,
      BROWN_300,
      BLUE_GREY_300)
  );

  private static final List<Integer> MATERIAL_500 = new ArrayList<>(Arrays.asList(
      RED_500,
      PINK_500,
      PURPLE_500,
      DEEP_PURPLE_500,
      INDIGO_500,
      BLUE_500,
      LIGHT_BLUE_500,
      CYAN_500,
      TEAL_500,
      GREEN_500,
      LIGHT_GREEN_500,
//      LIME_500,
      AMBER_500,
      ORANGE_500,
      DEEP_ORANGE_500,
      BROWN_500,
      BLUE_GREY_500)
  );

  private static final SparseIntArray MATERIAL_500_TO_700 = new SparseIntArray() {{
    put(RED_500, RED_700);
    put(PINK_500, PINK_700);
    put(PURPLE_500, PURPLE_700);
    put(DEEP_PURPLE_500, DEEP_PURPLE_700);
    put(INDIGO_500, INDIGO_700);
    put(BLUE_500, BLUE_700);
    put(LIGHT_BLUE_500, LIGHT_BLUE_700);
    put(CYAN_500, CYAN_700);
    put(TEAL_500, TEAL_700);
    put(GREEN_500, GREEN_700);
    put(LIGHT_GREEN_500, LIGHT_GREEN_700);
//    put(LIME_500, LIME_700);
    put(AMBER_500, AMBER_700);
    put(ORANGE_500, ORANGE_700);
    put(DEEP_ORANGE_500, DEEP_ORANGE_700);
    put(BROWN_500, BROWN_700);
    put(BLUE_GREY_500, BLUE_GREY_700);
  }};

  private static final SparseIntArray MATERIAL_300_TO_700 = new SparseIntArray() {{
    put(RED_300, RED_700);
    put(PINK_300, PINK_700);
    put(PURPLE_300, PURPLE_700);
    put(DEEP_PURPLE_300, DEEP_PURPLE_700);
    put(INDIGO_300, INDIGO_700);
    put(BLUE_300, BLUE_700);
    put(LIGHT_BLUE_300, LIGHT_BLUE_700);
    put(CYAN_300, CYAN_700);
    put(TEAL_300, TEAL_700);
    put(GREEN_300, GREEN_700);
    put(LIGHT_GREEN_300, LIGHT_GREEN_700);
    put(LIME_300, LIME_700);
    put(AMBER_300, AMBER_700);
    put(ORANGE_300, ORANGE_700);
    put(DEEP_ORANGE_300, DEEP_ORANGE_700);
    put(BROWN_300, BROWN_700);
    put(BLUE_GREY_300, BLUE_GREY_700);
  }};

  private static final ColorGenerator MATERIAL_GENERATOR = ColorGenerator.create(MATERIAL_500);

  public static int generateFor(@NonNull String name) {
    return MATERIAL_GENERATOR.getColor(name);
  }

  public static Optional<Integer> getStatusTinted(int color) {
    int statusTinted = MATERIAL_500_TO_700.get(color, -1);
    return statusTinted == -1 ? Optional.<Integer>absent() : Optional.of(statusTinted);
  }

}
