package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.StringUtil;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.emoji.ObsoleteEmoji;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class EmojiUtil {
  private static final Pattern EMOJI_PATTERN = Pattern.compile("^(?:(?:[\u00a9\u00ae\u203c\u2049\u2122\u2139\u2194-\u2199\u21a9-\u21aa\u231a-\u231b\u2328\u23cf\u23e9-\u23f3\u23f8-\u23fa\u24c2\u25aa-\u25ab\u25b6\u25c0\u25fb-\u25fe\u2600-\u2604\u260e\u2611\u2614-\u2615\u2618\u261d\u2620\u2622-\u2623\u2626\u262a\u262e-\u262f\u2638-\u263a\u2648-\u2653\u2660\u2663\u2665-\u2666\u2668\u267b\u267f\u2692-\u2694\u2696-\u2697\u2699\u269b-\u269c\u26a0-\u26a1\u26aa-\u26ab\u26b0-\u26b1\u26bd-\u26be\u26c4-\u26c5\u26c8\u26ce-\u26cf\u26d1\u26d3-\u26d4\u26e9-\u26ea\u26f0-\u26f5\u26f7-\u26fa\u26fd\u2702\u2705\u2708-\u270d\u270f\u2712\u2714\u2716\u271d\u2721\u2728\u2733-\u2734\u2744\u2747\u274c\u274e\u2753-\u2755\u2757\u2763-\u2764\u2795-\u2797\u27a1\u27b0\u27bf\u2934-\u2935\u2b05-\u2b07\u2b1b-\u2b1c\u2b50\u2b55\u3030\u303d\u3297\u3299\ud83c\udc04\ud83c\udccf\ud83c\udd70-\ud83c\udd71\ud83c\udd7e-\ud83c\udd7f\ud83c\udd8e\ud83c\udd91-\ud83c\udd9a\ud83c\ude01-\ud83c\ude02\ud83c\ude1a\ud83c\ude2f\ud83c\ude32-\ud83c\ude3a\ud83c\ude50-\ud83c\ude51\u200d\ud83c\udf00-\ud83d\uddff\ud83d\ude00-\ud83d\ude4f\ud83d\ude80-\ud83d\udeff\ud83e\udd00-\ud83e\uddff\udb40\udc20-\udb40\udc7f]|\u200d[\u2640\u2642]|[\ud83c\udde6-\ud83c\uddff]{2}|.[\u20e0\u20e3\ufe0f]+)+)+$");
  private static final String  EMOJI_REGEX   = "[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\s]";

  private EmojiUtil() {}

  /**
   * This will return all ways we know of expressing a singular emoji. This is to aid in search,
   * where some platforms may send an emoji we've locally marked as 'obsolete'.
   */
  public static @NonNull Set<String> getAllRepresentations(@NonNull String emoji) {
    Set<String> out = new HashSet<>();

    out.add(emoji);

    for (ObsoleteEmoji obsoleteEmoji : EmojiSource.getLatest().getObsolete()) {
      if (obsoleteEmoji.getObsolete().equals(emoji)) {
        out.add(obsoleteEmoji.getReplaceWith());
      } else if (obsoleteEmoji.getReplaceWith().equals(emoji)) {
        out.add(obsoleteEmoji.getObsolete());
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
    String canonical = EmojiSource.getLatest().getVariationsToCanonical().get(emoji);
    return canonical != null ? canonical : emoji;
  }

  public static boolean isCanonicallyEqual(@NonNull String left, @NonNull String right) {
    return getCanonicalRepresentation(left).equals(getCanonicalRepresentation(right));
  }

  /**
   * Converts the provided emoji string into a single drawable, if possible.
   */
  public static @Nullable Drawable convertToDrawable(@NonNull Context context, @Nullable String emoji) {
    if (Util.isEmpty(emoji)) {
      return null;
    } else {
      return EmojiProvider.getEmojiDrawable(context, emoji);
    }
  }

  /**
   * True if the text is likely a single, valid emoji. Otherwise false.
   *
   * We do a two-tier check: first using our own knowledge of emojis (which could be incomplete),
   * followed by a more wide check for all of the valid emoji unicode ranges (which could lead to
   * some false positives). YMMV.
   */
  public static boolean isEmoji(@Nullable String text) {
    if (Util.isEmpty(text)) {
      return false;
    }

    if (StringUtil.getGraphemeCount(text) != 1) {
      return false;
    }

    EmojiParser.CandidateList candidates = EmojiProvider.getCandidates(text);

    return (candidates != null && candidates.size() > 0) || EMOJI_PATTERN.matcher(text).matches();
  }

  public static String stripEmoji(@Nullable String text) {
    if (text == null) {
      return text;
    }

    return text.replaceAll(EMOJI_REGEX, "");
  }
}
