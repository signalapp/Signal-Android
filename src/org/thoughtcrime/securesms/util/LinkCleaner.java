package org.thoughtcrime.securesms.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.util.Patterns;

public class LinkCleaner {

  private final static String CLEANING_REGEX = "(?i)(\\?|&)(utm_[a-z_]*|ocid|fbclid|igshid)=([^&]+)";

  public static String cleanText(String text) {
    Matcher linksMatcher = Patterns.WEB_URL.matcher(text);
    String originalLink, cleanLink;
    while (linksMatcher.find()) {
      originalLink = linksMatcher.group();
      cleanLink    = cleanLink(originalLink);
      if (!originalLink.equals(cleanLink)) {
        text = text.replace(originalLink, cleanLink);
      }
    }
    return text;
  }

  private static String cleanLink(String link) {
    Matcher regexMatcher = Pattern.compile(CLEANING_REGEX).matcher(link);
    if (regexMatcher.find()) {
      link = firstMatch(regexMatcher, link);
      while (regexMatcher.find()) {
        link = removeTracker(link, regexMatcher.group(0));
      }
      link = cleanUpEndResult(link);
    }
    return link;
  }

  private static String removeTracker(String link, String tracker) {
    return link.replace(tracker, "");
  }

  private static String firstMatch(Matcher regexMatcher, String link) {
    if (regexMatcher.group(1).equals("?")) {
      return removeTracker(link, regexMatcher.group(0).substring(1));
    } else {
      return removeTracker(link, regexMatcher.group(0));
    }
  }

  private static String cleanUpEndResult(String link) {
    link = link.replace("?&", "?");
    if (link.charAt(link.length() - 1) == '?') {
      link = link.substring(0, link.length() - 1);
    }
    return link;
  }
}

