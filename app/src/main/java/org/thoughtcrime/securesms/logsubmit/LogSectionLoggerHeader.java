package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Because the actual contents of this section are paged from the database, this class just has a header and no content.
 */
public class LogSectionLoggerHeader implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGGER";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return "";
  }

  @Override
  public boolean hasContent() {
    return false;
  }
}
