package org.thoughtcrime.securesms.keyvalue;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EmojiValues extends SignalStoreValues {

  public static final List<String> DEFAULT_REACTIONS_LIST = Arrays.asList("\u2764\ufe0f",
                                                                          "\ud83d\udc4d",
                                                                          "\ud83d\udc4e",
                                                                          "\ud83d\ude02",
                                                                          "\ud83d\ude2e",
                                                                          "\ud83d\ude22");

  private static final String PREFIX               = "emojiPref__";
  private static final String NEXT_SCHEDULED_CHECK = PREFIX + "next_scheduled_check";
  private static final String REACTIONS_LIST       = PREFIX + "reactions_list";

  EmojiValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {

  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.singletonList(REACTIONS_LIST);
  }

  public long getNextScheduledCheck() {
    return getStore().getLong(NEXT_SCHEDULED_CHECK, 0);
  }

  public void setNextScheduledCheck(long nextScheduledCheck) {
    putLong(NEXT_SCHEDULED_CHECK, nextScheduledCheck);
  }

  public void setPreferredVariation(@NonNull String emoji) {
    String canonical = EmojiUtil.getCanonicalRepresentation(emoji);

    if (canonical.equals(emoji)) {
      getStore().beginWrite().remove(PREFIX + canonical).apply();
    } else {
      putString(PREFIX + canonical, emoji);
    }
  }

  public @NonNull String getPreferredVariation(@NonNull String emoji) {
    String canonical = EmojiUtil.getCanonicalRepresentation(emoji);

    return getString(PREFIX + canonical, emoji);
  }

  public @NonNull List<String> getReactions() {
    String list = getString(REACTIONS_LIST, "");
    if (TextUtils.isEmpty(list)) {
      return DEFAULT_REACTIONS_LIST;
    } else {
      return Arrays.asList(list.split(","));
    }
  }

  public void setReactions(List<String> reactions) {
    putString(REACTIONS_LIST, Util.join(reactions, ","));
  }
}
