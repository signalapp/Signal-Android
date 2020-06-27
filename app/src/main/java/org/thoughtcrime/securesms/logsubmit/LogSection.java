package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;

interface LogSection {
  /**
   * The title to show at the top of the log section.
   */
  @NonNull String getTitle();

  /**
   * The full content of your log section. We use a {@link CharSequence} instead of a
   * {@link List<LogLine> } for performance reasons. Scrubbing large swaths of text is faster than
   * one line at a time.
   */
  @NonNull CharSequence getContent(@NonNull Context context);
}
