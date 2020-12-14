package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LogSectionThreads implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "THREADS";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    StringBuilder builder = new StringBuilder();

    List<Thread> threads = new ArrayList<>(Thread.getAllStackTraces().keySet());
    Collections.sort(threads, (lhs, rhs) -> Long.compare(lhs.getId(), rhs.getId()));

    for (Thread thread : threads) {
      builder.append("[").append(thread.getId()).append("] ").append(thread.getName()).append("\n");
    }

    return builder;
  }
}
