package org.thoughtcrime.securesms.linkpreview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.stickers.StickerUrl;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public final class LinkPreviewUtil {

  private static final Pattern DOMAIN_PATTERN        = Pattern.compile("^(https?://)?([^/]+).*$");
  private static final Pattern ALL_ASCII_PATTERN     = Pattern.compile("^[\\x00-\\x7F]*$");
  private static final Pattern ALL_NON_ASCII_PATTERN = Pattern.compile("^[^\\x00-\\x7F]*$");
  private static final Pattern STICKER_URL_PATTERN   = Pattern.compile("^.*#pack_id=(.*)&pack_key=(.*)$");

  /**
   * @return All whitelisted URLs in the source text.
   */
  public static @NonNull List<Link> findWhitelistedUrls(@NonNull String text) {
    SpannableString spannable = new SpannableString(text);
    boolean         found     = Linkify.addLinks(spannable, Linkify.WEB_URLS);

    if (!found) {
      return Collections.emptyList();
    }

    return Stream.of(spannable.getSpans(0, spannable.length(), URLSpan.class))
                 .map(span -> new Link(span.getURL(), spannable.getSpanStart(span)))
                 .filter(link -> isWhitelistedLinkUrl(link.getUrl()))
                 .toList();
  }

  /**
   * @return True if the host is present in the link whitelist.
   */
  public static boolean isWhitelistedLinkUrl(@Nullable String linkUrl) {
    if (linkUrl == null)                      return false;
    if (StickerUrl.isValidShareLink(linkUrl)) return true;

    HttpUrl url = HttpUrl.parse(linkUrl);
    return url != null                                   &&
           !TextUtils.isEmpty(url.scheme())              &&
           "https".equals(url.scheme())                  &&
           LinkPreviewDomains.LINKS.contains(url.host()) &&
           isLegalUrl(linkUrl);
  }

  /**
   * @return True if the top-level domain is present in the media whitelist.
   */
  public static boolean isWhitelistedMediaUrl(@Nullable String mediaUrl) {
    if (mediaUrl == null) return false;

    HttpUrl url = HttpUrl.parse(mediaUrl);
    return url != null                                                &&
           !TextUtils.isEmpty(url.scheme())                           &&
           "https".equals(url.scheme())                               &&
           LinkPreviewDomains.IMAGES.contains(url.topPrivateDomain()) &&
           isLegalUrl(mediaUrl);
  }

  public static boolean isLegalUrl(@NonNull String url) {
    Matcher matcher = DOMAIN_PATTERN.matcher(url);

    if (matcher.matches()) {
      String domain        = matcher.group(2);
      String cleanedDomain = domain.replaceAll("\\.", "");

      return ALL_ASCII_PATTERN.matcher(cleanedDomain).matches() ||
             ALL_NON_ASCII_PATTERN.matcher(cleanedDomain).matches();
    } else {
      return false;
    }
  }
}
