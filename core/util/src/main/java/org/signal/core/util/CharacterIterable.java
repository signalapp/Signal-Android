package org.signal.core.util;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Iterator;

/**
 * Iterates over a string treating a surrogate pair and a grapheme cluster a single character.
 */
public final class CharacterIterable implements Iterable<String> {

  private final String string;

  public CharacterIterable(@NonNull String string) {
    this.string = string;
  }

  @Override
  public @NonNull Iterator<String> iterator() {
    return new CharacterIterator();
  }

  private class CharacterIterator implements Iterator<String> {
    private static final int UNINITIALIZED = -2;

    private final BreakIteratorCompat breakIterator;

    private int lastIndex = UNINITIALIZED;

    CharacterIterator() {
      this.breakIterator = Build.VERSION.SDK_INT >= 24 ? new AndroidIcuBreakIterator(string)
                                                       : new FallbackBreakIterator(string);
    }

    @Override
    public boolean hasNext() {
      if (lastIndex == UNINITIALIZED) {
        lastIndex = breakIterator.first();
      }
      return !breakIterator.isDone(lastIndex);
    }

    @Override
    public String next() {
      int firstIndex = lastIndex;
      lastIndex = breakIterator.next();
      return string.substring(firstIndex, lastIndex);
    }
  }

  private interface BreakIteratorCompat {
    int first();

    int next();

    boolean isDone(int index);
  }

  /**
   * An BreakIteratorCompat implementation that delegates calls to `android.icu.text.BreakIterator`.
   * This class handles grapheme clusters fine but requires Android API >= 24.
   */
  @RequiresApi(24)
  private static class AndroidIcuBreakIterator implements BreakIteratorCompat {
    private final android.icu.text.BreakIterator breakIterator = android.icu.text.BreakIterator.getCharacterInstance();

    public AndroidIcuBreakIterator(@NonNull String string) {
      breakIterator.setText(string);
    }

    @Override
    public int first() {
      return breakIterator.first();
    }

    @Override
    public int next() {
      return breakIterator.next();
    }

    @Override
    public boolean isDone(int index) {
      return index == android.icu.text.BreakIterator.DONE;
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
  private static class FallbackBreakIterator implements BreakIteratorCompat {
    private final java.text.BreakIterator breakIterator = java.text.BreakIterator.getCharacterInstance();

    public FallbackBreakIterator(@NonNull String string) {
      breakIterator.setText(string);
    }

    @Override
    public int first() {
      return breakIterator.first();
    }

    @Override
    public int next() {
      return breakIterator.next();
    }

    @Override
    public boolean isDone(int index) {
      return index == java.text.BreakIterator.DONE;
    }
  }
}
