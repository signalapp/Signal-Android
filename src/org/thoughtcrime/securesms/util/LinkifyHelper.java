package org.thoughtcrime.securesms.util;

import android.text.util.Linkify;
import android.widget.TextView;

import java.util.regex.Pattern;

public class LinkifyHelper {
  private final static Pattern GEO_URL_PATTERN = Pattern.compile("geo:([\\-0-9.]+),([\\-0-9.]+)(?:,([\\-0-9.]+))?(?:\\?(.*))?", Pattern.CASE_INSENSITIVE);

  public static void addLinks(TextView text) {
    Linkify.addLinks(text, Linkify.ALL);
    Linkify.addLinks(text, GEO_URL_PATTERN, null);
  }
}
