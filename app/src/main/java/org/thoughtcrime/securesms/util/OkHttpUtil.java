package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.ResponseBody;

public final class OkHttpUtil {

  private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=[\"']?([a-zA-Z0-9\\\\-]+)[\"']?");

  private OkHttpUtil() {}

  public static byte[] readAsBytes(@NonNull InputStream bodyStream, long sizeLimit) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    byte[] buffer      = new byte[(int) ByteUnit.KILOBYTES.toBytes(32)];
    int    readLength  = 0;
    int    totalLength = 0;

    while ((readLength = bodyStream.read(buffer)) >= 0) {
      if (totalLength + readLength > sizeLimit) {
        throw new IOException("Exceeded maximum size during read!");
      }

      outputStream.write(buffer, 0, readLength);
      totalLength += readLength;
    }

    return outputStream.toByteArray();
  }
  public static String readAsString(@NonNull ResponseBody body, long sizeLimit) throws IOException {
    if (body.contentLength() > sizeLimit) {
      throw new IOException("Content-Length exceeded maximum size!");
    }

    byte[]    data        = readAsBytes(body.byteStream(), sizeLimit);
    MediaType contentType = body.contentType();
    Charset   charset     = contentType != null ? contentType.charset(null) : null;

    charset = charset == null ? getHtmlCharset(new String(data)) : charset;

    return new String(data, Objects.requireNonNull(charset));
  }

  private static @NonNull Charset getHtmlCharset(String html) {
    Matcher charsetMatcher = CHARSET_PATTERN.matcher(html);
    if (charsetMatcher.find() && charsetMatcher.groupCount() > 0) {
      try {
        return Objects.requireNonNull(Charset.forName(fromDoubleEncoded(charsetMatcher.group(1))));
      } catch (Exception ignored) {}
    }
    return StandardCharsets.UTF_8;
  }

  private static @NonNull String fromDoubleEncoded(@NonNull String html) {
    return HtmlCompat.fromHtml(HtmlCompat.fromHtml(html, 0).toString(), 0).toString();
  }
}
