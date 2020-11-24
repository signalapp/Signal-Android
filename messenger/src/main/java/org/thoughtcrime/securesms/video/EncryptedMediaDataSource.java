package org.thoughtcrime.securesms.video;


import android.annotation.TargetApi;
import android.media.MediaDataSource;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@TargetApi(Build.VERSION_CODES.M)
public class EncryptedMediaDataSource extends MediaDataSource {

  private final AttachmentSecret attachmentSecret;
  private final File             mediaFile;
  private final byte[]           random;
  private final long             length;

  public EncryptedMediaDataSource(@NonNull AttachmentSecret attachmentSecret, @NonNull File mediaFile, @Nullable byte[] random, long length) {
    this.attachmentSecret = attachmentSecret;
    this.mediaFile        = mediaFile;
    this.random           = random;
    this.length           = length;
  }

  @Override
  public int readAt(long position, byte[] bytes, int offset, int length) throws IOException {
    if (random == null) return readAtClassic(position, bytes, offset, length);
    else                return readAtModern(position, bytes, offset, length);
  }

  private int readAtClassic(long position, byte[] bytes, int offset, int length) throws IOException {
    InputStream inputStream     = ClassicDecryptingPartInputStream.createFor(attachmentSecret, mediaFile);
    byte[]      buffer          = new byte[4096];
    long        headerRemaining = position;

    while (headerRemaining > 0) {
      int read = inputStream.read(buffer, 0, Util.toIntExact(Math.min((long)buffer.length, headerRemaining)));

      if (read == -1) return -1;

      headerRemaining -= read;
    }

    int returnValue = inputStream.read(bytes, offset, length);
    inputStream.close();
    return returnValue;
  }

  private int readAtModern(long position, byte[] bytes, int offset, int length) throws IOException {
    assert(random != null);

    InputStream inputStream = ModernDecryptingPartInputStream.createFor(attachmentSecret, random, mediaFile, position);
    int         returnValue = inputStream.read(bytes, offset, length);

    inputStream.close();

    return returnValue;
  }

  @Override
  public long getSize() throws IOException {
    return length;
  }

  @Override
  public void close() throws IOException {

  }
}
