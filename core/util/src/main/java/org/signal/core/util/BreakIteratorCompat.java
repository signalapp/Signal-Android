package org.signal.core.util;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Iterator;

public abstract class BreakIteratorCompat implements Iterable<CharSequence> {
  public static final int          DONE = -1;
  private             CharSequence charSequence;

  public abstract int first();

  public abstract int next();

  public void setText(CharSequence charSequence) {
    this.charSequence = charSequence;
  }

  public static BreakIteratorCompat getInstance() {
    if (Build.VERSION.SDK_INT >= 24) {
      return new AndroidIcuBreakIterator();
    } else {
      return new FallbackBreakIterator();
    }
  }

  public int countBreaks() {
    int breakCount = 0;

    first();

    while (next() != DONE) {
      breakCount++;
    }

    return breakCount;
  }

  @Override
  public @NonNull Iterator<CharSequence> iterator() {
    return new Iterator<CharSequence>() {

      int index1 = BreakIteratorCompat.this.first();
      int index2 = BreakIteratorCompat.this.next();

      @Override
      public boolean hasNext() {
        return index2 != DONE;
      }

      @Override
      public CharSequence next() {
        CharSequence c = index2 != DONE ? charSequence.subSequence(index1, index2) : "";

        index1 = index2;
        index2 = BreakIteratorCompat.this.next();

        return c;
      }
    };
  }

  /**
   * Take {@param atMost} graphemes from the start of string.
   */
  public final CharSequence take(int atMost) {
    if (atMost <= 0) return "";

    StringBuilder stringBuilder = new StringBuilder(charSequence.length());
    int           count         = 0;

    for (CharSequence grapheme : this) {
      stringBuilder.append(grapheme);

      count++;

      if (count >= atMost) break;
    }

    return stringBuilder.toString();
  }

  /**
   * An BreakIteratorCompat implementation that delegates calls to `android.icu.text.BreakIterator`.
   * This class handles grapheme clusters fine but requires Android API >= 24.
   */
  @RequiresApi(24)
  private static class AndroidIcuBreakIterator extends BreakIteratorCompat {
    private final android.icu.text.BreakIterator breakIterator = android.icu.text.BreakIterator.getCharacterInstance();

    @Override
    public int first() {
      return breakIterator.first();
    }

    @Override
    public int next() {
      return breakIterator.next();
    }

    @Override
    public void setText(CharSequence charSequence) {
      super.setText(charSequence);
      if (Build.VERSION.SDK_INT >= 29) {
        breakIterator.setText(charSequence);
      } else {
        breakIterator.setText(charSequence.toString());
      }
    }
  }

  /**
   * An BreakIteratorCompat implementation that delegates calls to `java.text.BreakIterator`.
   * This class may or may not handle grapheme clusters well depending on the underlying implementation.
   * In the emulator, API 23 implements ICU version of the BreakIterator so that it handles grapheme
   * clusters fine. But API 21 implements RuleBasedIterator which does not handle grapheme clusters.
   * <p>
   * If it doesn't handle grapheme clusters correctly, in most cases the combined characters are
   * broken up into pieces when the code tries to trim a string. For example, an emoji that is
   * a combination of a person, gender and skin tone, trimming the character using this class may result
   * in trimming the parts of the character, e.g. a dark skin frowning woman emoji may result in
   * a neutral skin frowning woman emoji.
   */
  private static class FallbackBreakIterator extends BreakIteratorCompat {
    private final java.text.BreakIterator breakIterator = java.text.BreakIterator.getCharacterInstance();

    @Override
    public int first() {
      return breakIterator.first();
    }

    @Override
    public int next() {
      return breakIterator.next();
    }

    @Override
    public void setText(CharSequence charSequence) {
      super.setText(charSequence);
      breakIterator.setText(charSequence.toString());
    }
  }
}
