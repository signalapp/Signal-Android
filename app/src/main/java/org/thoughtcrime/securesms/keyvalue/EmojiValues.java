package org.thoughtcrime.securesms.keyvalue;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
  private static final String SEARCH_VERSION       = PREFIX + "search_version";
  private static final String SEARCH_LANGUAGE      = PREFIX + "search_language";
  private static final String LAST_SEARCH_CHECK    = PREFIX + "last_search_check";
  private static final String JUMBO_EMOJI_DOWNLOAD = PREFIX + "jumbo_emoji_v";

  public static final String NO_LANGUAGE = "NO_LANGUAGE";

  EmojiValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putInteger(SEARCH_VERSION, 0);
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.singletonList(REACTIONS_LIST);
  }

  public long getNextScheduledImageCheck() {
    return getStore().getLong(NEXT_SCHEDULED_CHECK, 0);
  }

  public void setNextScheduledImageCheck(long nextScheduledCheck) {
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

  /**
   * Returns a list usable emoji that the user has selected as their defaults. If any stored reactions are unreadable, it will provide a default.
   * For raw access to the unfiltered list of reactions, see {@link #getRawReactions()}.
   */
  public @NonNull List<String> getReactions() {
    List<String> raw = getRawReactions();
    List<String> out = new ArrayList<>(DEFAULT_REACTIONS_LIST.size());

    for (int i = 0; i < DEFAULT_REACTIONS_LIST.size(); i++) {
      if (raw.size() > i && EmojiUtil.isEmoji(raw.get(i))) {
        out.add(raw.get(i));
      } else {
        out.add(DEFAULT_REACTIONS_LIST.get(i));
      }
    }

    return out;
  }

  /**
   * A raw list of the default reactions the user has selected. It will be empty if there hasn't been any custom ones set. It may contain unrenderable emoji.
   * This is primarily here for syncing to storage service. You probably want {@link #getReactions()} for everything else.
   */
  public @NonNull List<String> getRawReactions() {
    String list = getString(REACTIONS_LIST, "");
    if (TextUtils.isEmpty(list)) {
      return Collections.emptyList();
    } else {
      return Arrays.asList(list.split(","));
    }
  }

  public void setReactions(@NonNull List<String> reactions) {
    putString(REACTIONS_LIST, Util.join(reactions, ","));
  }

  public void onSearchIndexUpdated(int version, @NonNull String language) {
    getStore().beginWrite()
              .putInteger(SEARCH_VERSION, version)
              .putString(SEARCH_LANGUAGE, language)
              .apply();
  }

  public boolean hasSearchIndex() {
    return getSearchVersion() > 0 && getSearchLanguage() != null;
  }

  public int getSearchVersion() {
    return getInteger(SEARCH_VERSION, 0);
  }

  public @Nullable String getSearchLanguage() {
    return getString(SEARCH_LANGUAGE, null);
  }

  public long getLastSearchIndexCheck() {
    return getLong(LAST_SEARCH_CHECK, 0);
  }

  public void setLastSearchIndexCheck(long time) {
    putLong(LAST_SEARCH_CHECK, time);
  }

  public void addJumboEmojiSheet(int version, String sheet) {
    Set<String> sheets = getJumboEmojiSheets(version);
    sheets.add(sheet);
    getStore().beginWrite()
              .putString(JUMBO_EMOJI_DOWNLOAD + version, Util.join(sheets, ","))
              .apply();
  }

  public HashSet<String> getJumboEmojiSheets(int version) {
    return new HashSet<>(Arrays.asList(getStore().getString(JUMBO_EMOJI_DOWNLOAD + version, "").split(",")));
  }

  public void clearJumboEmojiSheets(int version) {
    getStore().beginWrite()
              .remove(JUMBO_EMOJI_DOWNLOAD + version)
              .apply();
  }
}
