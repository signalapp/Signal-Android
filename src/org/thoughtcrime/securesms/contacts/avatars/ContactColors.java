package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.SparseIntArray;

import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.color.MaterialColors;
import org.thoughtcrime.securesms.color.ThemeType;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ContactColors {

  public static final MaterialColor UNKNOWN_COLOR = MaterialColors.GREY;
  
  public static MaterialColor generateFor(@NonNull String name) {
    return MaterialColors.CONVERSATION_PALETTE.get(Math.abs(name.hashCode()) % MaterialColors.CONVERSATION_PALETTE.size());
  }

  public static MaterialColor getGroupColor(Context context) {
    final int actionBarColor = context.getResources().getColor(R.color.textsecure_primary);
    final int statusBarColor = context.getResources().getColor(R.color.textsecure_primary_dark);

    return new MaterialColor(new HashMap<String, Integer>()) {
      @Override
      public int toConversationColor(ThemeType themeType) {
        return UNKNOWN_COLOR.toConversationColor(themeType);
      }

      @Override
      public int toActionBarColor(ThemeType themeType) {
        return actionBarColor;
      }

      @Override
      public int toStatusBarColor(ThemeType themeType) {
        return statusBarColor;
      }

      @Override
      public String serialize() {
        return "group_color";
      }
    };

  }

//  public static Optional<Integer> getStatusTinted(int color) {
//    int statusTinted = MATERIAL_500_TO_700.get(color, -1);
//    return statusTinted == -1 ? Optional.<Integer>absent() : Optional.of(statusTinted);
//  }

}
