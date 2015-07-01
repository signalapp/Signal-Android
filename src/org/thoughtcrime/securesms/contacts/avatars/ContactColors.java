package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.color.MaterialColors;

import java.util.HashMap;

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
      public int toConversationColor(@NonNull Context context) {
        return UNKNOWN_COLOR.toConversationColor(context);
      }

      @Override
      public int toActionBarColor(@NonNull Context context) {
        return actionBarColor;
      }

      @Override
      public int toStatusBarColor(@NonNull Context context) {
        return statusBarColor;
      }

      @Override
      public String serialize() {
        return "group_color";
      }
    };
  }
}
