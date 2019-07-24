package org.thoughtcrime.securesms.stickers;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.google.android.gms.common.util.Hex;

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

    if (TextUtils.isEmpty(packId) || TextUtils.isEmpty(packKey) || !isValidHex(packId) || !isValidHex(packKey)) {
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
      String packId  = matcher.group(1);
      String packKey = matcher.group(2);

      if (isValidHex(packId) && isValidHex(packKey)) {
        return Optional.of(new Pair<>(packId, packKey));
      }
    }

    return Optional.absent();
  }

  public static String createShareLink(@NonNull String packId, @NonNull String packKey) {
    return "https://signal.org/addstickers/#pack_id=" + packId + "&pack_key=" + packKey;
  }

  private static boolean isValidHex(String value) {
    try {
      Hex.stringToBytes(value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
