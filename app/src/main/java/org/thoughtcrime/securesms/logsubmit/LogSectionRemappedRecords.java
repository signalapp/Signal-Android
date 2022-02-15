package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.signal.core.util.AsciiArt;
import org.thoughtcrime.securesms.database.SignalDatabase;

/**
 * Renders data pertaining to sender key. While all private info is obfuscated, this is still only intended to be printed for internal users.
 */
public class LogSectionRemappedRecords implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "REMAPPED RECORDS";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    StringBuilder builder = new StringBuilder();

    builder.append("--- Recipients").append("\n\n");
    try (Cursor cursor = SignalDatabase.remappedRecords().getAllRecipients()) {
      builder.append(AsciiArt.tableFor(cursor)).append("\n\n");
    }

    builder.append("--- Threads").append("\n\n");
    try (Cursor cursor = SignalDatabase.remappedRecords().getAllThreads()) {
      builder.append(AsciiArt.tableFor(cursor)).append("\n");
    }

    return builder;
  }
}
