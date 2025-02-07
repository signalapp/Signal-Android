package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.AppDependencies;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class LogSectionCurrentThreadDump implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LAST THREAD DUMP";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
    StringBuilder out = new StringBuilder();

    for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
      Thread thread = entry.getKey();
      out.append("-- [").append(thread.getId()).append("] ")
         .append(thread.getName()).append(" (").append(thread.getState()).append(")\n");

      for (StackTraceElement element : entry.getValue()) {
        out.append(element.toString()).append("\n");
      }

      out.append("\n");
    }

    return out;
  }
}
