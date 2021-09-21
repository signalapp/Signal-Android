package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In-memory book keeping of newly created part files to prevent them from being
 * deleted prematurely.
 */
public final class PartFileProtector {

  private static final long PROTECTED_DURATION = TimeUnit.MINUTES.toMillis(10);

  private static final Map<String, Long> protectedFiles = new HashMap<>();

  public static synchronized File protect(@NonNull CreateFile createFile) throws IOException {
    File file = createFile.create();
    protectedFiles.put(file.getAbsolutePath(), System.currentTimeMillis());
    return file;
  }

  public static synchronized boolean isProtected(File file) {
    long timestamp = 0;

    Long protectedTimestamp = protectedFiles.get(file.getAbsolutePath());
    if (protectedTimestamp != null) {
      timestamp = Math.max(protectedTimestamp, file.lastModified());
    }

    boolean isProtected = timestamp > System.currentTimeMillis() - PROTECTED_DURATION;

    if (!isProtected) {
      protectedFiles.remove(file.getAbsolutePath());
    }

    return isProtected;
  }

  interface CreateFile {
    @NonNull File create() throws IOException;
  }
}
