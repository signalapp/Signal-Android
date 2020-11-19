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
    return LogStyleParser.TRACE_PLACEHOLDER;
  }
}
