package org.thoughtcrime.securesms.backup;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.InputStream;
import java.io.IOException;

public class MultiFileInputStream extends InputStream {
  private int currentChunk = -1;
  private InputStream currentInputStream = null;
  ContentResolver contentResolver;
  Uri[] uris;
  boolean noFilesLeft;

  public MultiFileInputStream(ContentResolver contentResolver,
                              Uri[] uris) {
    this.contentResolver = contentResolver;
    this.uris = uris;
    this.noFilesLeft = (uris.length == 0);
  }

  public void close() throws IOException {
    if (currentInputStream != null) {
      currentInputStream.close();
    }
  }

  public void swapFiles() throws IOException, SecurityException {
    if (currentInputStream != null) {
      currentInputStream.close();
    }
    currentChunk++;
    Uri uri = uris[currentChunk];
    currentInputStream = contentResolver.openInputStream(uri);
  }

  public int read() throws IOException {
    byte[] b = new byte[1];
    if (read(b) == -1) {
      return -1;
    }
    return b[0];
  }

  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  public int read(byte[] b, int off, int len) throws IOException {
    if (currentInputStream == null) {
      if (noFilesLeft) {
        return -1;
      }
      swapFiles();
    }
    while (true) {
      int bytesRead = currentInputStream.read(b, off, len);
      boolean eofRead = (bytesRead == -1);
      if (eofRead) {
        if (noFilesLeft) {
          return -1;
        } else {
          swapFiles();
        }
      } else {
        return bytesRead;
      }
    }
  }
}
