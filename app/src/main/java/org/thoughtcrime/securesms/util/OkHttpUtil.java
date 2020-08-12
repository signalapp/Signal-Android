package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.ResponseBody;

import static okhttp3.internal.Util.UTF_8;

public final class OkHttpUtil {

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
    Charset   charset     = contentType != null ? contentType.charset(UTF_8) : UTF_8;

    return new String(data, Objects.requireNonNull(charset));
  }
}
