package org.thoughtcrime.securesms.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryFileDescriptor implements Closeable {

  private static final String TAG = Log.tag(MemoryFileDescriptor.class);

  private static Boolean supported;

  private final ParcelFileDescriptor parcelFileDescriptor;
  private final AtomicLong           sizeEstimate;

  /**
   * Does this device support memory file descriptor.
   */
  public synchronized static boolean supported() {
    if (supported == null) {
      try {
        int fileDescriptor = FileUtils.createMemoryFileDescriptor("CHECK");

        if (fileDescriptor < 0) {
          supported = false;
          Log.w(TAG, "MemoryFileDescriptor is not available.");
        } else {
          supported = true;
          ParcelFileDescriptor.adoptFd(fileDescriptor).close();
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
    return supported;
  }

  /**
   * memfd files do not show on the available RAM, so we must track our allocations in addition.
   */
  private static long sizeOfAllMemoryFileDescriptors;

  private MemoryFileDescriptor(@NonNull ParcelFileDescriptor parcelFileDescriptor, long sizeEstimate) {
    this.parcelFileDescriptor = parcelFileDescriptor;
    this.sizeEstimate         = new AtomicLong(sizeEstimate);
  }

  /**
   * @param debugName    The name supplied in name is used as a filename and will be displayed
   *                     as the target of the corresponding symbolic link in the directory
   *                     /proc/self/fd/.  The displayed name is always prefixed with memfd:
   *                     and serves only for debugging purposes.  Names do not affect the
   *                     behavior of the file descriptor, and as such multiple files can have
   *                     the same name without any side effects.
   * @param sizeEstimate An estimated upper bound on this file. This is used to check there will be
   *                     enough RAM available and to register with a global counter of reservations.
   *                     Use zero to avoid RAM check.
   * @return MemoryFileDescriptor
   * @throws MemoryLimitException If there is not enough available RAM to comfortably fit this file.
   * @throws MemoryFileCreationException If fails to create a memory file descriptor.
   */
  public static MemoryFileDescriptor newMemoryFileDescriptor(@NonNull Context context,
                                                             @NonNull String debugName,
                                                             long sizeEstimate)
      throws MemoryFileException
  {
    if (sizeEstimate < 0) throw new IllegalArgumentException();

    if (sizeEstimate > 0) {
      ActivityManager            activityManager = ServiceUtil.getActivityManager(context);
      ActivityManager.MemoryInfo memoryInfo      = new ActivityManager.MemoryInfo();

      synchronized (MemoryFileDescriptor.class) {
        activityManager.getMemoryInfo(memoryInfo);

        long remainingRam = memoryInfo.availMem - memoryInfo.threshold - sizeEstimate - sizeOfAllMemoryFileDescriptors;

        if (remainingRam <= 0) {
          NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
          Log.w(TAG, String.format("Not enough RAM available without taking the system into a low memory state.%n" +
                                   "Available: %s%n" +
                                   "Low memory threshold: %s%n" +
                                   "Requested: %s%n" +
                                   "Total MemoryFileDescriptor limit: %s%n" +
                                   "Shortfall: %s",
          numberFormat.format(memoryInfo.availMem),
          numberFormat.format(memoryInfo.threshold),
          numberFormat.format(sizeEstimate),
          numberFormat.format(sizeOfAllMemoryFileDescriptors),
          numberFormat.format(remainingRam)
          ));
          throw new MemoryLimitException();
        }

        sizeOfAllMemoryFileDescriptors += sizeEstimate;
      }
    }

    int fileDescriptor = FileUtils.createMemoryFileDescriptor(debugName);

    if (fileDescriptor < 0) {
      Log.w(TAG, "Failed to create file descriptor: " + fileDescriptor);
      throw new MemoryFileCreationException();
    }

    return new MemoryFileDescriptor(ParcelFileDescriptor.adoptFd(fileDescriptor), sizeEstimate);
  }

  @Override
  public void close() throws IOException {
    try {
      clearAndRemoveAllocation();
    } catch (Exception e) {
      Log.w(TAG, "Failed to clear data in MemoryFileDescriptor", e);
    } finally {
      parcelFileDescriptor.close();
    }
  }

  private void clearAndRemoveAllocation() throws IOException {
    clear();

    long oldEstimate = sizeEstimate.getAndSet(0);

    synchronized (MemoryFileDescriptor.class) {
      sizeOfAllMemoryFileDescriptors -= oldEstimate;
    }
  }

  /** Rewinds and clears all bytes. */
  private void clear() throws IOException {
    long size;
    try (FileInputStream fileInputStream = new FileInputStream(getFileDescriptor())) {
      FileChannel channel = fileInputStream.getChannel();
      size = channel.size();

      if (size == 0) return;

      channel.position(0);
    }
    byte[] zeros = new byte[16 * 1024];

    try (FileOutputStream output = new FileOutputStream(getFileDescriptor())) {
      while (size > 0) {
        int limit = (int) Math.min(size, zeros.length);

        output.write(zeros, 0, limit);

        size -= limit;
      }
    }
  }

  public FileDescriptor getFileDescriptor() {
    return parcelFileDescriptor.getFileDescriptor();
  }

  public ParcelFileDescriptor getParcelFileDescriptor() {
    return parcelFileDescriptor;
  }

  public void seek(long position) throws IOException {
    try (FileInputStream fileInputStream = new FileInputStream(getFileDescriptor())) {
      fileInputStream.getChannel().position(position);
    }
  }

  public long size() throws IOException {
    try (FileInputStream fileInputStream = new FileInputStream(getFileDescriptor())) {
      return fileInputStream.getChannel().size();
    }
  }

  public static class MemoryFileException extends IOException {
  }

  private static final class MemoryLimitException extends MemoryFileException {
  }

  private static final class MemoryFileCreationException extends MemoryFileException {
  }
}
