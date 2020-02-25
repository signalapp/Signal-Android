package org.thoughtcrime.securesms.util.cjkv;

import androidx.annotation.Nullable;

public final class CJKVUtil {

  private CJKVUtil() {
  }

  public static boolean isCJKV(@Nullable String value) {
    if (value == null || value.length() == 0) {
      return true;
    }

    for (int offset = 0; offset < value.length(); ) {
      int codepoint = Character.codePointAt(value, offset);

      if (!isCodepointCJKV(codepoint)) {
        return false;
      }

      offset += Character.charCount(codepoint);
    }

    return true;
  }

  private static boolean isCodepointCJKV(int codepoint) {
    if (codepoint == (int)' ') return true;

    Character.UnicodeBlock block = Character.UnicodeBlock.of(codepoint);

    return Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)                  ||
           Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)      ||
           Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B.equals(block)      ||
           Character.UnicodeBlock.CJK_COMPATIBILITY.equals(block)                       ||
           Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS.equals(block)                 ||
           Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS.equals(block)            ||
           Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT.equals(block) ||
           Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT.equals(block)                 ||
           Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION.equals(block)             ||
           Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS.equals(block)         ||
           Character.UnicodeBlock.KANGXI_RADICALS.equals(block)                         ||
           Character.UnicodeBlock.IDEOGRAPHIC_DESCRIPTION_CHARACTERS.equals(block)      ||
           Character.UnicodeBlock.HIRAGANA.equals(block)                                ||
           Character.UnicodeBlock.KATAKANA.equals(block)                                ||
           Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS.equals(block)            ||
           Character.UnicodeBlock.HANGUL_JAMO.equals(block)                             ||
           Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO.equals(block)               ||
           Character.UnicodeBlock.HANGUL_SYLLABLES.equals(block)                        ||
           Character.isIdeographic(codepoint);
  }
}
