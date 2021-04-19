package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.emoji.EmojiUtil;

import java.util.Collections;
import java.util.List;

public class EmojiValues extends SignalStoreValues {

  private static final String PREFIX = "emojiPref__";
  private static final String NEXT_SCHEDULED_CHECK = PREFIX + "next_scheduled_check";

  EmojiValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {

  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
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
}
