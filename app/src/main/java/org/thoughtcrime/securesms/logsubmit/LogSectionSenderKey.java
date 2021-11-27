package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.signal.core.util.AsciiArt;
import org.thoughtcrime.securesms.database.SignalDatabase;

/**
 * Renders data pertaining to sender key. While all private info is obfuscated, this is still only intended to be printed for internal users.
 */
public class LogSectionSenderKey implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "SENDER KEY";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    StringBuilder builder = new StringBuilder();

    builder.append("--- Sender Keys Created By This Device").append("\n\n");
    try (Cursor cursor = SignalDatabase.senderKeys().getAllCreatedBySelf()) {
      builder.append(AsciiArt.tableFor(cursor)).append("\n\n");
    }

    builder.append("--- Sender Key Shared State").append("\n\n");
    try (Cursor cursor = SignalDatabase.senderKeyShared().getAllSharedWithCursor()) {
      builder.append(AsciiArt.tableFor(cursor)).append("\n");
    }

    return builder;
  }
}
