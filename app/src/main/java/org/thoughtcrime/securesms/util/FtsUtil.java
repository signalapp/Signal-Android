package org.thoughtcrime.securesms.util;

import android.database.DatabaseUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.HashSet;
import java.util.Set;

public final class FtsUtil {
  private static final Set<Character> BANNED_CHARACTERS = new HashSet<>();
  static {
    // Several ranges of invalid ASCII characters
    for (int i = 33; i <= 47; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 58; i <= 64; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 91; i <= 96; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 123; i <= 126; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
  }

  private FtsUtil() {}

  /**
   * Unfortunately {@link DatabaseUtils#sqlEscapeString(String)} is not sufficient for our purposes.
   * MATCH queries have a separate format of their own that disallow most "special" characters.
   *
   * Also, SQLite can't search for apostrophes, meaning we can't normally find words like "I'm".
   * However, if we replace the apostrophe with a space, then the query will find the match.
   */
  public static @NonNull String sanitize(@NonNull String query) {
    StringBuilder out = new StringBuilder();

    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      if (!BANNED_CHARACTERS.contains(c)) {
        out.append(c);
      } else if (c == '\'') {
        out.append(' ');
      }
    }

    return out.toString();
  }

  /**
   * Sanitizes the string (via {@link #sanitize(String)}) and appends * at the right spots such that each token in the query will be treated as a prefix.
   */
  public static @NonNull String createPrefixMatchString(@NonNull String query) {
    query = FtsUtil.sanitize(query);

    return Stream.of(query.split(" "))
                 .map(String::trim)
                 .filter(s -> s.length() > 0)
                 .map(FtsUtil::fixQuotes)
                 .collect(StringBuilder::new, (sb, s) -> sb.append(s).append("* "))
                 .toString();
  }

  private static String fixQuotes(String s) {
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
