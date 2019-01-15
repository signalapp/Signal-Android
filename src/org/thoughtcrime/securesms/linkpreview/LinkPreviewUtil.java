package org.thoughtcrime.securesms.linkpreview;

import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import com.annimon.stream.Stream;

import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

public final class LinkPreviewUtil {

  /**
   * @return All whitelisted URLs in the source text.
   */
  public static @NonNull List<String> findWhitelistedUrls(@NonNull String text) {
    SpannableString spannable = new SpannableString(text);
    boolean         found     = Linkify.addLinks(spannable, Linkify.WEB_URLS);

    if (!found) {
      return Collections.emptyList();
    }

    return Stream.of(spannable.getSpans(0, spannable.length(), URLSpan.class))
                 .map(URLSpan::getURL)
                 .filter(LinkPreviewUtil::isWhitelistedLinkUrl)
                 .toList();
  }

  /**
   * @return True if the host is present in the link whitelist.
   */
  public static boolean isWhitelistedLinkUrl(@NonNull String linkUrl) {
    HttpUrl url = HttpUrl.parse(linkUrl);
    return url != null                      &&
           !TextUtils.isEmpty(url.scheme()) &&
           "https".equals(url.scheme())     &&
           LinkPreviewDomains.LINKS.contains(url.host());
  }

  /**
   * @return True if the top-level domain is present in the media whitelist.
   */
  public static boolean isWhitelistedMediaUrl(@NonNull String mediaUrl) {
    HttpUrl url = HttpUrl.parse(mediaUrl);
    return url != null                      &&
           !TextUtils.isEmpty(url.scheme()) &&
           "https".equals(url.scheme())     &&
           LinkPreviewDomains.IMAGES.contains(url.topPrivateDomain());
  }
}
