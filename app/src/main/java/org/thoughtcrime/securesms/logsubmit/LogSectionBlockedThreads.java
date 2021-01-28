package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Map;

public class LogSectionBlockedThreads implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "BLOCKED THREADS";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
    StringBuilder                    out    = new StringBuilder();

    for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
      if (entry.getKey().getState() == Thread.State.BLOCKED) {
        Thread thread = entry.getKey();
        out.append("-- [").append(thread.getId()).append("] ")
           .append(thread.getName()).append(" (").append(thread.getState().toString()).append(")\n");

        for (StackTraceElement element : entry.getValue()) {
          out.append(element.toString()).append("\n");
        }

        out.append("\n");
      }
    }

    return out.length() == 0 ? "None" : out;
  }
}
