package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class LogSectionLogcat implements LogSection {

  /** Silences the View tag, which some OEM builds spam once per draw pass, while leaving every other tag at verbose. */
  private static final String FILTER_SPEC = "View:S *:V";

  @Override
  public @NonNull String getTitle() {
    return "LOGCAT";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    try {
      final Process        process        = Runtime.getRuntime().exec("logcat -d " + FILTER_SPEC);
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      final StringBuilder  log            = new StringBuilder();
      final String         separator      = System.lineSeparator();

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        log.append(line);
        log.append(separator);
      }
      return log.toString();
    } catch (IOException ioe) {
      return "Failed to retrieve.";
    }
  }
}
