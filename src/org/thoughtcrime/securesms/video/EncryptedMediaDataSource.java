package org.thoughtcrime.securesms.video;


import android.media.MediaDataSource;
import android.os.Build;
import android.support.annotation.RequiresApi;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.IOException;

@RequiresApi(api = Build.VERSION_CODES.M)
public class EncryptedMediaDataSource extends MediaDataSource {

  private final File         mediaFile;
  private final MasterSecret masterSecret;

  public EncryptedMediaDataSource(MasterSecret masterSecret, File mediaFile) {
    this.mediaFile    = mediaFile;
    this.masterSecret = masterSecret;
  }

  @Override
  public int readAt(long position, byte[] bytes, int offset, int length) throws IOException {
    DecryptingPartInputStream inputStream     = new DecryptingPartInputStream(mediaFile, masterSecret);
    byte[]                    buffer          = new byte[4096];
    long                      headerRemaining = position;

    while (headerRemaining > 0) {
      int read = inputStream.read(buffer, 0, Util.toIntExact(Math.min((long)buffer.length, headerRemaining)));

      if (read == -1) return -1;

      headerRemaining -= read;
    }

    int returnValue = inputStream.read(bytes, offset, length);
    inputStream.close();
    return returnValue;
  }

  @Override
  public long getSize() throws IOException {
    DecryptingPartInputStream inputStream = new DecryptingPartInputStream(mediaFile, masterSecret);
    byte[]                    buffer      = new byte[4096];
    long                      size        = 0;

    int read;

    while ((read = inputStream.read(buffer)) != -1) {
      size += read;
    }

    return size;
  }

  @Override
  public void close() throws IOException {

  }
}
