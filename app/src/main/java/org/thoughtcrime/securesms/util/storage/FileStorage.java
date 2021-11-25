package org.thoughtcrime.securesms.util.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.StreamUtil;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the storage of custom files.
 */
public final class FileStorage {

  /**
   * Saves the provided input stream as a new file.
   */
  @WorkerThread
  public static @NonNull String save(@NonNull Context context,
                                     @NonNull InputStream inputStream,
                                     @NonNull String directoryName,
                                     @NonNull String fileNameBase,
                                     @NonNull String extension)
      throws IOException
  {
    File directory = context.getDir(directoryName, Context.MODE_PRIVATE);
    File file      = File.createTempFile(fileNameBase, "." + extension, directory);

    StreamUtil.copy(inputStream, getOutputStream(context, file));

    return file.getName();
  }

  @WorkerThread
  public static @NonNull InputStream read(@NonNull Context context,
                                          @NonNull String directoryName,
                                          @NonNull String filename)
      throws IOException
  {
    File directory = context.getDir(directoryName, Context.MODE_PRIVATE);
    File file      = new File(directory, filename);

    return getInputStream(context, file);
  }

  @WorkerThread
  public static @NonNull List<String> getAll(@NonNull Context context,
                                             @NonNull String directoryName,
                                             @NonNull String fileNameBase)
  {
    return getAllFiles(context, directoryName, fileNameBase).stream()
                                                            .map(File::getName)
                                                            .collect(Collectors.toList());
  }

  @WorkerThread
  public static @NonNull List<File> getAllFiles(@NonNull Context context,
                                                @NonNull String directoryName,
                                                @NonNull String fileNameBase)
  {
    File   directory = context.getDir(directoryName, Context.MODE_PRIVATE);
    File[] allFiles  = directory.listFiles(pathname -> pathname.getName().contains(fileNameBase));

    if (allFiles != null) {
      return Arrays.asList(allFiles);
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Note that you will always get a file back, but that file may not exist on disk.
   */
  @WorkerThread
  public static @NonNull File getFile(@NonNull Context context,
                                      @NonNull String directoryName,
                                      @NonNull String filename)
  {
    return new File(context.getDir(directoryName, Context.MODE_PRIVATE), filename);
  }

  private static @NonNull OutputStream getOutputStream(@NonNull Context context, File outputFile) throws IOException {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    return ModernEncryptingPartOutputStream.createFor(attachmentSecret, outputFile, true).second;
  }

  private static @NonNull InputStream getInputStream(@NonNull Context context, File inputFile) throws IOException {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    return ModernDecryptingPartInputStream.createFor(attachmentSecret, inputFile, 0);
  }
}
