package org.thoughtcrime.securesms.video;

import android.media.MediaDataSource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Create via {@link EncryptedMediaDataSource}.
 * <p>
 * A {@link MediaDataSource} that points to an encrypted file.
 * <p>
 * It is "modern" compared to the {@link ClassicEncryptedMediaDataSource}. And "modern" refers to
 * the presence of a random part of the key supplied in the constructor.
 */
@RequiresApi(23)
final class ModernEncryptedMediaDataSource extends MediaDataSource {

  private final AttachmentSecret attachmentSecret;
  private final File             mediaFile;
  private final byte[]           random;
  private final long             length;

  ModernEncryptedMediaDataSource(@NonNull AttachmentSecret attachmentSecret, @NonNull File mediaFile, @Nullable byte[] random, long length) {
    this.attachmentSecret = attachmentSecret;
    this.mediaFile        = mediaFile;
    this.random           = random;
    this.length           = length;
  }

  @Override
  public int readAt(long position, byte[] bytes, int offset, int length) throws IOException {
    try (InputStream inputStream = createInputStream(position)) {
      int totalRead = 0;

      while (length > 0) {
        int read = inputStream.read(bytes, offset, length);

        if (read == -1) {
          if (totalRead == 0) {
            return -1;
          } else {
            return totalRead;
          }
        }

        length    -= read;
        offset    += read;
        totalRead += read;
      }

      return totalRead;
    }
  }

  @Override
  public long getSize() {
    return length;
  }

  @Override
  public void close() {
  }

  private InputStream createInputStream(long position) throws IOException {
    if (random == null) {
      return ModernDecryptingPartInputStream.createFor(attachmentSecret, mediaFile, position);
    } else {
      return ModernDecryptingPartInputStream.createFor(attachmentSecret, random, mediaFile, position);
    }
  }
}
