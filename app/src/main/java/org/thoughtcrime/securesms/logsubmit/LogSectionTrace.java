package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.tracing.Tracer;
import org.thoughtcrime.securesms.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class LogSectionTrace implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "TRACE";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    try (ByteArrayOutputStream outputStream     = new ByteArrayOutputStream();
         GZIPOutputStream      compressedStream = new GZIPOutputStream(outputStream))
    {
      compressedStream.write(Tracer.getInstance().serialize());
      compressedStream.flush();
      compressedStream.close();

      outputStream.flush();
      outputStream.close();

      return Base64.encodeBytes(outputStream.toByteArray());
    } catch (IOException e) {
      return "";
    }
  }
}
