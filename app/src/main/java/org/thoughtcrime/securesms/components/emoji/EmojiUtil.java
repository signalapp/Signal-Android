package org.thoughtcrime.securesms.components.emoji;

import androidx.annotation.NonNull;

import org.whispersystems.libsignal.util.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EmojiUtil {

  private static final Map<String, String> VARIATION_MAP = new HashMap<>();

  static {
    for (EmojiPageModel page : EmojiPages.DATA_PAGES) {
      for (Emoji emoji : page.getDisplayEmoji()) {
        for (String variation : emoji.getVariations()) {
          VARIATION_MAP.put(variation, emoji.getValue());
        }
      }
    }
  }

  public static final int MAX_EMOJI_LENGTH;
  static {
    int max = 0;
    for (EmojiPageModel page : EmojiPages.DATA_PAGES) {
      for (String emoji : page.getEmoji()) {
        max = Math.max(max, emoji.length());
      }
    }
    MAX_EMOJI_LENGTH = max;
  }

  private EmojiUtil() {}

  /**
   * This will return all ways we know of expressing a singular emoji. This is to aid in search,
   * where some platforms may send an emoji we've locally marked as 'obsolete'.
   */
  public static @NonNull Set<String> getAllRepresentations(@NonNull String emoji) {
    Set<String> out = new HashSet<>();

    out.add(emoji);

    for (Pair<String, String> pair : EmojiPages.OBSOLETE) {
      if (pair.first().equals(emoji)) {
        out.add(pair.second());
      } else if (pair.second().equals(emoji)) {
        out.add(pair.first());
      }
    }

    return out;
  }

  /**
   * When provided an emoji that is a skin variation of another, this will return the default yellow
   * version. This is to aid in search, so using a variation will still find all emojis tagged with
   * the default version.
   *
   * If the emoji has no skin variations, this function will return the original emoji.
   */
  public static @NonNull String getCanonicalRepresentation(@NonNull String emoji) {
    String canonical = VARIATION_MAP.get(emoji);
    return canonical != null ? canonical : emoji;
  }
}
