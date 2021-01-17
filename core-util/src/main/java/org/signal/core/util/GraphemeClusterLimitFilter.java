package org.signal.core.util;

import android.text.InputFilter;
import android.text.Spanned;

import org.signal.core.util.logging.Log;

/**
 * This filter will constrain edits not to make the number of character breaks of the text
 * greater than the specified maximum.
 * <p>
 * This means it will limit to a maximum number of grapheme clusters.
 */
public final class GraphemeClusterLimitFilter implements InputFilter {

  private static final String TAG = Log.tag(GraphemeClusterLimitFilter.class);

  private final BreakIteratorCompat breakIteratorCompat;
  private final int                 max;

  public GraphemeClusterLimitFilter(int max) {
    this.breakIteratorCompat = BreakIteratorCompat.getInstance();
    this.max                 = max;
  }

  @Override
  public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    CharSequence sourceFragment = source.subSequence(start, end);

    CharSequence head = dest.subSequence(0, dstart);
    CharSequence tail = dest.subSequence(dend, dest.length());

    breakIteratorCompat.setText(String.format("%s%s%s", head, sourceFragment, tail));
    int length = breakIteratorCompat.countBreaks();

    if (length > max) {
      breakIteratorCompat.setText(sourceFragment);
      int          sourceLength  = breakIteratorCompat.countBreaks();
      CharSequence trimmedSource = breakIteratorCompat.take(sourceLength - (length - max));

      breakIteratorCompat.setText(String.format("%s%s%s", head, trimmedSource, tail));
      int newExpectedCount = breakIteratorCompat.countBreaks();
      if (newExpectedCount > max) {
        Log.w(TAG, "Failed to create string under the required length " + newExpectedCount);
        return "";
      }

      return trimmedSource;
    }

    return source;
  }
}
