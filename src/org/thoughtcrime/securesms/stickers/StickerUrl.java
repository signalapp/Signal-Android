package org.thoughtcrime.securesms.stickers;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages creating and parsing the various sticker pack URLs.
 */
public class StickerUrl {

  private static final Pattern STICKER_URL_PATTERN = Pattern.compile("^https://signal\\.org/addstickers/#pack_id=(.*)&pack_key=(.*)$");

  public static Optional<Pair<String, String>> parseActionUri(@Nullable Uri uri) {
    if (uri == null) return Optional.absent();

    String packId  = uri.getQueryParameter("pack_id");
    String packKey = uri.getQueryParameter("pack_key");

    if (TextUtils.isEmpty(packId) || TextUtils.isEmpty(packKey)) {
      return Optional.absent();
    }

    return Optional.of(new Pair<>(packId, packKey));
  }

  public static @NonNull Uri createActionUri(@NonNull String packId, @NonNull String packKey) {
    return Uri.parse(String.format("sgnl://addstickers?pack_id=%s&pack_key=%s", packId, packKey));
  }

  public static boolean isValidShareLink(@Nullable String url) {
    return parseShareLink(url).isPresent();
  }

  public static @NonNull Optional<Pair<String, String>> parseShareLink(@Nullable String url) {
    if (url == null) return Optional.absent();

    Matcher matcher = STICKER_URL_PATTERN.matcher(url);

    if (matcher.matches() && matcher.groupCount() == 2) {
      return Optional.of(new Pair<>(matcher.group(1), matcher.group(2)));
    }

    return Optional.absent();
  }

  public static String createShareLink(@NonNull String packId, @NonNull String packKey) {
    return "https://signal.org/addstickers/#pack_id=" + packId + "&pack_key=" + packKey;
  }
}
