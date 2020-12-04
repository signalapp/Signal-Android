package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

public class LogSectionTrace implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "TRACE";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return LogStyleParser.TRACE_PLACEHOLDER;
  }
}
