/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Utility to patch creation/modification timestamps in an MP4 file's container
 * metadata (mvhd, tkhd, mdhd boxes) without loading the entire file into memory.
 * <p>
 * Only reads box headers (8-16 bytes each) to navigate the structure, then writes
 * the timestamp fields in-place. Safe for arbitrarily large video files.
 */
public final class Mp4TimestampUtil {

  private static final String TAG = Log.tag(Mp4TimestampUtil.class);

  /** Seconds between 1904-01-01 and 1970-01-01 (MP4 epoch offset). */
  private static final long MP4_EPOCH_OFFSET = 2082844800L;

  private static final String BOX_MOOV = "moov";
  private static final String BOX_TRAK = "trak";
  private static final String BOX_MDIA = "mdia";
  private static final String BOX_MVHD = "mvhd";
  private static final String BOX_TKHD = "tkhd";
  private static final String BOX_MDHD = "mdhd";

  private Mp4TimestampUtil() {}

  /**
   * Updates creation and modification timestamps in the MP4 container metadata.
   *
   * @param file      The MP4 file to modify in-place.
   * @param timestamp Milliseconds since Unix epoch.
   */
  public static void setCreationTime(@NonNull File file, long timestamp) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
      patchTimestamps(raf.getFD(), timestamp);
    }
  }

  /**
   * Updates creation and modification timestamps in the MP4 container metadata.
   *
   * @param fd        A readable and writable file descriptor for the MP4 file.
   * @param timestamp Milliseconds since Unix epoch.
   */
  public static void setCreationTime(@NonNull FileDescriptor fd, long timestamp) throws IOException {
    patchTimestamps(fd, timestamp);
  }

  private static void patchTimestamps(@NonNull FileDescriptor fd, long timestamp) throws IOException {
    long mp4Time = (timestamp / 1000) + MP4_EPOCH_OFFSET;

    try {
      long moovOffset = findTopLevelBox(fd, BOX_MOOV);
      if (moovOffset < 0) {
        Log.w(TAG, "No moov box found");
        return;
      }

      long moovSize = readBoxSize(fd, moovOffset);
      if (moovSize < 8) return;

      long moovEnd        = moovOffset + moovSize;
      long moovChildStart = moovOffset + boxHeaderSize(fd, moovOffset);

      patchFullBoxTimestamps(fd, moovChildStart, moovEnd, BOX_MVHD, mp4Time);
      patchTrackBoxes(fd, moovChildStart, moovEnd, mp4Time);
    } catch (ErrnoException | InterruptedIOException e) {
      throw new IOException("Failed to patch MP4 timestamps", e);
    }
  }

  private static void patchTrackBoxes(@NonNull FileDescriptor fd, long searchStart,
                                      long searchEnd, long mp4Time)
      throws ErrnoException, InterruptedIOException {
    long pos = searchStart;
    while (pos < searchEnd) {
      BoxInfo box = readBoxInfo(fd, pos);
      if (box == null || box.size < 8) break;

      if (BOX_TRAK.equals(box.type)) {
        long trakChildStart = pos + box.headerSize;
        long trakEnd        = pos + box.size;

        patchFullBoxTimestamps(fd, trakChildStart, trakEnd, BOX_TKHD, mp4Time);

        long mdiaOffset = findChildBox(fd, trakChildStart, trakEnd, BOX_MDIA);
        if (mdiaOffset >= 0) {
          long mdiaSize       = readBoxSize(fd, mdiaOffset);
          long mdiaChildStart = mdiaOffset + boxHeaderSize(fd, mdiaOffset);
          long mdiaEnd        = mdiaOffset + mdiaSize;

          patchFullBoxTimestamps(fd, mdiaChildStart, mdiaEnd, BOX_MDHD, mp4Time);
        }
      }

      pos += box.size;
    }
  }

  /**
   * Finds a child box of the given type and patches its creation/modification timestamps in-place.
   * Works for FullBox types (mvhd, tkhd, mdhd) which store version + flags after the header,
   * followed by creation_time and modification_time.
   */
  private static void patchFullBoxTimestamps(@NonNull FileDescriptor fd, long searchStart,
                                             long searchEnd, @NonNull String boxType,
                                             long mp4Time) throws ErrnoException, InterruptedIOException {
    long boxOffset = findChildBox(fd, searchStart, searchEnd, boxType);
    if (boxOffset < 0) return;

    BoxInfo info = readBoxInfo(fd, boxOffset);
    if (info == null) return;

    // FullBox layout: [header] [version: 1 byte] [flags: 3 bytes] [creation_time] [modification_time] ...
    long versionOffset = boxOffset + info.headerSize;
    byte[] versionBuf = new byte[1];
    pread(fd, versionBuf, versionOffset);
    int version = versionBuf[0] & 0xFF;

    long timestampOffset = versionOffset + 4;
    Os.lseek(fd, timestampOffset, OsConstants.SEEK_SET);

    if (version >= 1) {
      ByteBuffer buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
      buf.putLong(mp4Time);
      buf.putLong(mp4Time);
      Os.write(fd, buf.array(), 0, 16);
    } else {
      ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
      buf.putInt((int) mp4Time);
      buf.putInt((int) mp4Time);
      Os.write(fd, buf.array(), 0, 8);
    }
  }

  private static long findTopLevelBox(@NonNull FileDescriptor fd, @NonNull String type) throws ErrnoException, InterruptedIOException {
    long pos = 0;
    while (true) {
      BoxInfo box = readBoxInfo(fd, pos);
      if (box == null || box.size < 8) return -1;
      if (type.equals(box.type)) return pos;
      pos += box.size;
    }
  }

  private static long findChildBox(@NonNull FileDescriptor fd, long start, long end, @NonNull String type) throws ErrnoException, InterruptedIOException {
    long pos = start;
    while (pos + 8 <= end) {
      BoxInfo box = readBoxInfo(fd, pos);
      if (box == null || box.size < 8) return -1;
      if (type.equals(box.type)) return pos;
      pos += box.size;
    }
    return -1;
  }

  private static long readBoxSize(@NonNull FileDescriptor fd, long offset) throws ErrnoException, InterruptedIOException {
    BoxInfo info = readBoxInfo(fd, offset);
    return info != null ? info.size : -1;
  }

  private static int boxHeaderSize(@NonNull FileDescriptor fd, long offset) throws ErrnoException, InterruptedIOException {
    byte[] buf = new byte[4];
    pread(fd, buf, offset);
    long rawSize = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).getInt() & 0xFFFFFFFFL;
    return rawSize == 1 ? 16 : 8;
  }

  @Nullable
  private static BoxInfo readBoxInfo(@NonNull FileDescriptor fd, long offset) throws ErrnoException, InterruptedIOException {
    byte[] header = new byte[16];

    if (pread(fd, header, 0, 8, offset) < 8) return null;

    long   rawSize    = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt() & 0xFFFFFFFFL;
    String type       = new String(header, 4, 4, StandardCharsets.US_ASCII);
    int    headerSize = 8;

    if (rawSize == 1) {
      if (pread(fd, header, 8, 8, offset + 8) < 8) return null;
      rawSize    = ByteBuffer.wrap(header, 8, 8).order(ByteOrder.BIG_ENDIAN).getLong();
      headerSize = 16;
    }

    return new BoxInfo(type, rawSize, headerSize);
  }

  private static int pread(@NonNull FileDescriptor fd, byte[] buf, long offset) throws ErrnoException, InterruptedIOException {
    return pread(fd, buf, 0, buf.length, offset);
  }

  private static int pread(@NonNull FileDescriptor fd, byte[] buf, int bufOffset, int length, long fileOffset) throws ErrnoException, InterruptedIOException {
    Os.lseek(fd, fileOffset, OsConstants.SEEK_SET);
    int totalRead = 0;
    while (totalRead < length) {
      int read = Os.read(fd, buf, bufOffset + totalRead, length - totalRead);
      if (read <= 0) break;
      totalRead += read;
    }
    return totalRead;
  }

  private static final class BoxInfo {
    final String type;
    final long   size;
    final int    headerSize;

    BoxInfo(String type, long size, int headerSize) {
      this.type       = type;
      this.size       = size;
      this.headerSize = headerSize;
    }
  }
}

