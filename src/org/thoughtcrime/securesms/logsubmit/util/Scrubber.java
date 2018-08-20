/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.logsubmit.util;

import org.thoughtcrime.securesms.logging.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrub data for possibly sensitive information
 */
public class Scrubber {
  private static final String TAG = Scrubber.class.getSimpleName();
  private static final Pattern E164_PATTERN = Pattern.compile("\\+\\d{10,15}");

  private static final Pattern[] DEFAULTS = new Pattern[] {
      E164_PATTERN
  };

  private final Pattern[] patterns;
  public Scrubber(Pattern... patterns) {
    this.patterns = patterns;
  }

  public Scrubber() {
    this(DEFAULTS);
  }

  public String scrub(final String in) {
    Log.d(TAG, "scrubbing input");
    String out = in;
    for (Pattern pattern : patterns) {
      Matcher       matcher       = pattern.matcher(out);
      StringBuilder builder       = new StringBuilder();
      int           lastEndingPos = 0;

      while (matcher.find()) {
        builder.append(out.substring(lastEndingPos, matcher.start()));

        final String censored = matcher.group().substring(0,1)                                      +
                                new String(new char[matcher.group().length()-3]).replace("\0", "*") +
                                matcher.group().substring(matcher.group().length()-2);
        builder.append(censored);

        lastEndingPos = matcher.end();
        Log.i(TAG, "replacing a match on /" + pattern.toString() + "/ => " + censored);
      }
      builder.append(out.substring(lastEndingPos));
      out = builder.toString();
    }
    return out;
  }
}
