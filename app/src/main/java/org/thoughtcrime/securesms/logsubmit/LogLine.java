package org.thoughtcrime.securesms.logsubmit;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.List;
import java.util.regex.Pattern;

public interface LogLine {

  long getId();
  @NonNull String getText();
  @NonNull Style getStyle();

  static List<LogLine> fromText(@NonNull CharSequence text) {
    return Stream.of(Pattern.compile("\\n").split(text))
                 .map(s -> new SimpleLogLine(s, Style.NONE))
                 .map(line -> (LogLine) line)
                 .toList();
  }

  enum Style {
    NONE, VERBOSE, DEBUG, INFO, WARNING, ERROR
  }
}
