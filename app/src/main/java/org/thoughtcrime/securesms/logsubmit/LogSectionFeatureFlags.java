package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;

import java.util.Map;

public class LogSectionFeatureFlags implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "FEATURE FLAGS";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    StringBuilder        out          = new StringBuilder();
    Map<String, Boolean> memory       = FeatureFlags.getMemoryValues();
    Map<String, Boolean> disk         = FeatureFlags.getDiskValues();
    Map<String, Boolean> forced       = FeatureFlags.getForcedValues();
    int                  remoteLength = Stream.of(memory.keySet()).map(String::length).max(Integer::compareTo).orElse(0);
    int                  diskLength   = Stream.of(disk.keySet()).map(String::length).max(Integer::compareTo).orElse(0);
    int                  forcedLength = Stream.of(forced.keySet()).map(String::length).max(Integer::compareTo).orElse(0);

    out.append("-- Memory\n");
    for (Map.Entry<String, Boolean> entry : memory.entrySet()) {
      out.append(Util.rightPad(entry.getKey(), remoteLength)).append(": ").append(entry.getValue()).append("\n");
    }
    out.append("\n");

    out.append("-- Disk\n");
    for (Map.Entry<String, Boolean> entry : disk.entrySet()) {
      out.append(Util.rightPad(entry.getKey(), diskLength)).append(": ").append(entry.getValue()).append("\n");
    }
    out.append("\n");

    out.append("-- Forced\n");
    if (forced.isEmpty()) {
      out.append("None\n");
    } else {
      for (Map.Entry<String, Boolean> entry : forced.entrySet()) {
        out.append(Util.rightPad(entry.getKey(), forcedLength)).append(": ").append(entry.getValue()).append("\n");
      }
    }

    return out;
  }
}
