package org.thoughtcrime.securesms.logsubmit;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class LogStyleParser {

  private static final Map<String, LogLine.Style> STYLE_MARKERS = new HashMap<String, LogLine.Style>() {{
    put(" V ", LogLine.Style.VERBOSE);
    put(" D ", LogLine.Style.DEBUG);
    put(" I ", LogLine.Style.INFO);
    put(" W ", LogLine.Style.WARNING);
    put(" E ", LogLine.Style.ERROR);
  }};

  public static LogLine.Style parseStyle(@NonNull String text) {
    for (Map.Entry<String, LogLine.Style> entry : STYLE_MARKERS.entrySet()) {
      if (text.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return LogLine.Style.NONE;
  }
}
