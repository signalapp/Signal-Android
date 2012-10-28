package org.thoughtcrime.securesms.protocol;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;

public class Tag {

  public static final String WHITESPACE_TAG = "             ";

  public static boolean isTaggable(Context context, String message) {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

    return sp.getBoolean(ApplicationPreferencesActivity.WHITESPACE_PREF, true) &&
           message.matches(".*[^\\s].*")                                       &&
           message.replaceAll("\\s+$", "").length() + WHITESPACE_TAG.length() <= 158;
  }

  public static boolean isTagged(String message) {
    return message != null && message.matches(".*[^\\s]" + WHITESPACE_TAG + "$");
  }

  public static String getTaggedMessage(String message) {
    return message.replaceAll("\\s+$", "") + WHITESPACE_TAG;
  }

  public static String stripTag(String message) {
    if (isTagged(message))
      return message.substring(0, message.length() - WHITESPACE_TAG.length());

    return message;
  }

}
