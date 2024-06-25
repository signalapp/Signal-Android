package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.Util;

import java.util.Map;

public class LogSectionRemoteConfig implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "REMOTE CONFIG";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    StringBuilder       out           = new StringBuilder();
    Map<String, Object> memory        = RemoteConfig.getDebugMemoryValues();
    Map<String, Object> disk          = RemoteConfig.getDebugDiskValues();
    Map<String, Object> pending       = RemoteConfig.getDebugPendingDiskValues();
    int                 remoteLength  = Stream.of(memory.keySet()).map(String::length).max(Integer::compareTo).orElse(0);
    int                 diskLength    = Stream.of(disk.keySet()).map(String::length).max(Integer::compareTo).orElse(0);
    int                 pendingLength = Stream.of(pending.keySet()).map(String::length).max(Integer::compareTo).orElse(0);

    out.append("-- Memory\n");
    for (Map.Entry<String, Object> entry : memory.entrySet()) {
      out.append(Util.rightPad(entry.getKey(), remoteLength)).append(": ").append(entry.getValue()).append("\n");
    }
    out.append("\n");

    out.append("-- Current Disk\n");
    for (Map.Entry<String, Object> entry : disk.entrySet()) {
      out.append(Util.rightPad(entry.getKey(), diskLength)).append(": ").append(entry.getValue()).append("\n");
    }
    out.append("\n");

    out.append("-- Pending Disk\n");
    for (Map.Entry<String, Object> entry : pending.entrySet()) {
      out.append(Util.rightPad(entry.getKey(), pendingLength)).append(": ").append(entry.getValue()).append("\n");
    }
    out.append("\n");

    return out;
  }
}
