package org.thoughtcrime.securesms.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.SetUtil;
import org.thoughtcrime.securesms.stickers.StickerUrl;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public final class LinkUtil {

  private static final Pattern DOMAIN_PATTERN             = Pattern.compile("^(https?://)?([^/]+).*$");
  private static final Pattern ALL_ASCII_PATTERN          = Pattern.compile("^[\\x00-\\x7F]*$");
  private static final Pattern ALL_NON_ASCII_PATTERN      = Pattern.compile("^[^\\x00-\\x7F]*$");
  private static final Pattern ILLEGAL_CHARACTERS_PATTERN = Pattern.compile("[\u202C\u202D\u202E\u2500-\u25FF]");

  private static final Set<String> INVALID_TOP_LEVEL_DOMAINS = SetUtil.newHashSet("onion", "i2p");

  private LinkUtil() {}

  /**
   * @return True if URL is valid for link previews.
   */
  public static boolean isValidPreviewUrl(@Nullable String linkUrl) {
    if (linkUrl == null) {
      return false;
    }

    if (StickerUrl.isValidShareLink(linkUrl)) {
      return true;
    }

    HttpUrl url = HttpUrl.parse(linkUrl);
    return url != null &&
           !TextUtils.isEmpty(url.scheme()) &&
           "https".equals(url.scheme()) &&
           isLegalUrl(linkUrl, false, false);
  }

  /**
   * @return True if URL is valid, mostly useful for linkifying.
   */
  public static boolean isLegalUrl(@NonNull String url) {
    return isLegalUrl(url, true, false);
  }

  public static boolean isLegalUrl(@NonNull String url, boolean skipTopLevelDomainValidation, boolean requireTopLevelDomain) {
    if (ILLEGAL_CHARACTERS_PATTERN.matcher(url).find()) {
      return false;
    }

    Matcher matcher = DOMAIN_PATTERN.matcher(url);

    if (matcher.matches()) {
      String domain         = Objects.requireNonNull(matcher.group(2));
      String cleanedDomain  = domain.replaceAll("\\.", "");
      String topLevelDomain = parseTopLevelDomain(domain);

      boolean validCharacters = ALL_ASCII_PATTERN.matcher(cleanedDomain).matches() ||
                                ALL_NON_ASCII_PATTERN.matcher(cleanedDomain).matches();

      boolean validTopLevelDomain = (skipTopLevelDomainValidation || !INVALID_TOP_LEVEL_DOMAINS.contains(topLevelDomain)) &&
                                    (!requireTopLevelDomain || topLevelDomain != null);

      return validCharacters && validTopLevelDomain;
    } else {
      return false;
    }
  }

  private static @Nullable String parseTopLevelDomain(@NonNull String domain) {
    int periodIndex = domain.lastIndexOf(".");

    if (periodIndex >= 0 && periodIndex < domain.length() - 1) {
      return domain.substring(periodIndex + 1);
    } else {
      return null;
    }
  }
}
