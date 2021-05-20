package org.thoughtcrime.securesms.linkpreview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.annotation.SuppressLint;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.util.DateUtils;
import org.session.libsignal.utilities.guava.Optional;

import org.session.libsession.utilities.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public final class LinkPreviewUtil {

  private static final Pattern DOMAIN_PATTERN             = Pattern.compile("^(https?://)?([^/]+).*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ALL_ASCII_PATTERN          = Pattern.compile("^[\\x00-\\x7F]*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ALL_NON_ASCII_PATTERN      = Pattern.compile("^[^\\x00-\\x7F]*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern OPEN_GRAPH_TAG_PATTERN     = Pattern.compile("<\\s*meta[^>]*property\\s*=\\s*\"\\s*og:([^\"]+)\"[^>]*/?\\s*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ARTICLE_TAG_PATTERN        = Pattern.compile("<\\s*meta[^>]*property\\s*=\\s*\"\\s*article:([^\"]+)\"[^>]*/?\\s*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern OPEN_GRAPH_CONTENT_PATTERN = Pattern.compile("content\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern TITLE_PATTERN              = Pattern.compile("<\\s*title[^>]*>(.*)<\\s*/title[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern FAVICON_PATTERN            = Pattern.compile("<\\s*link[^>]*rel\\s*=\\s*\".*icon.*\"[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern FAVICON_HREF_PATTERN       = Pattern.compile("href\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

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
                 .filter(link -> isValidLinkUrl(link.getUrl()))
                 .toList();
  }

  /**
   * @return True if the host is valid.
   */
  public static boolean isValidLinkUrl(@Nullable String linkUrl) {
    if (linkUrl == null)                      return false;

    HttpUrl url = HttpUrl.parse(linkUrl);
    return url != null                                   &&
           !TextUtils.isEmpty(url.scheme())              &&
           "https".equals(url.scheme())                  &&
           isLegalUrl(linkUrl);
  }

  /**
   * @return True if the top-level domain is valid.
   */
  public static boolean isValidMediaUrl(@Nullable String mediaUrl) {
    if (mediaUrl == null) return false;

    HttpUrl url = HttpUrl.parse(mediaUrl);
    return url != null                                                &&
           !TextUtils.isEmpty(url.scheme())                           &&
           "https".equals(url.scheme())                               &&
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

  public static boolean isValidMimeType(@NonNull String url) {
    String[] validMimeType = {".jpg", ".png", ".gif", ".jpeg"};
    if (url.contains(".")) {
      for (String mimeType : validMimeType) {
        if (url.contains(mimeType)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  public static @NonNull OpenGraph parseOpenGraphFields(@Nullable String html) {
    return parseOpenGraphFields(html, text -> Html.fromHtml(text).toString());
  }

  static @NonNull OpenGraph parseOpenGraphFields(@Nullable String html, @NonNull HtmlDecoder htmlDecoder) {
    if (html == null) {
      return new OpenGraph(Collections.emptyMap(), null, null);
    }

    Map<String, String> openGraphTags    = new HashMap<>();
    Matcher             openGraphMatcher = OPEN_GRAPH_TAG_PATTERN.matcher(html);

    while (openGraphMatcher.find()) {
      String tag      = openGraphMatcher.group();
      String property = openGraphMatcher.groupCount() > 0 ? openGraphMatcher.group(1) : null;

      if (property != null) {
        Matcher contentMatcher = OPEN_GRAPH_CONTENT_PATTERN.matcher(tag);
        if (contentMatcher.find() && contentMatcher.groupCount() > 0) {
          String content = htmlDecoder.fromEncoded(contentMatcher.group(1));
          openGraphTags.put(property.toLowerCase(), content);
        }
      }
    }

    Matcher articleMatcher = ARTICLE_TAG_PATTERN.matcher(html);

    while (articleMatcher.find()) {
      String tag      = articleMatcher.group();
      String property = articleMatcher.groupCount() > 0 ? articleMatcher.group(1) : null;

      if (property != null) {
        Matcher contentMatcher = OPEN_GRAPH_CONTENT_PATTERN.matcher(tag);
        if (contentMatcher.find() && contentMatcher.groupCount() > 0) {
          String content = htmlDecoder.fromEncoded(contentMatcher.group(1));
          openGraphTags.put(property.toLowerCase(), content);
        }
      }
    }

    String htmlTitle  = "";
    String faviconUrl = "";

    Matcher titleMatcher = TITLE_PATTERN.matcher(html);
    if (titleMatcher.find() && titleMatcher.groupCount() > 0) {
      htmlTitle = htmlDecoder.fromEncoded(titleMatcher.group(1));
    }

    Matcher faviconMatcher = FAVICON_PATTERN.matcher(html);
    if (faviconMatcher.find()) {
      Matcher faviconHrefMatcher = FAVICON_HREF_PATTERN.matcher(faviconMatcher.group());
      if (faviconHrefMatcher.find() && faviconHrefMatcher.groupCount() > 0) {
        faviconUrl = faviconHrefMatcher.group(1);
      }
    }

    return new OpenGraph(openGraphTags, htmlTitle, faviconUrl);
  }

  public static final class OpenGraph {

    private final Map<String, String> values;

    private final @Nullable String htmlTitle;
    private final @Nullable String faviconUrl;

    private static final String KEY_TITLE            = "title";
    private static final String KEY_DESCRIPTION_URL  = "description";
    private static final String KEY_IMAGE_URL        = "image";
    private static final String KEY_PUBLISHED_TIME_1 = "published_time";
    private static final String KEY_PUBLISHED_TIME_2 = "article:published_time";
    private static final String KEY_MODIFIED_TIME_1  = "modified_time";
    private static final String KEY_MODIFIED_TIME_2  = "article:modified_time";

    public OpenGraph(@NonNull Map<String, String> values, @Nullable String htmlTitle, @Nullable String faviconUrl) {
      this.values     = values;
      this.htmlTitle  = htmlTitle;
      this.faviconUrl = faviconUrl;
    }

    public @NonNull Optional<String> getTitle() {
      return Optional.of(Util.getFirstNonEmpty(values.get(KEY_TITLE), htmlTitle));
    }

    public @NonNull Optional<String> getImageUrl() {
      return Optional.of(Util.getFirstNonEmpty(values.get(KEY_IMAGE_URL), faviconUrl));
    }

    @SuppressLint("ObsoleteSdkInt")
    public long getDate() {
      return Stream.of(values.get(KEY_PUBLISHED_TIME_1),
              values.get(KEY_PUBLISHED_TIME_2),
              values.get(KEY_MODIFIED_TIME_1),
              values.get(KEY_MODIFIED_TIME_2))
              .map(DateUtils::parseIso8601)
              .filter(time -> time > 0)
              .findFirst()
              .orElse(0L);
    }
  }

  public interface HtmlDecoder {
    @NonNull String fromEncoded(@NonNull String html);
  }

}
