package org.thoughtcrime.securesms.backup;

import android.content.Context;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MultiFileOutputStream29 extends OutputStream {
  private int currentChunk = -1;
  private int bytesRemaining = 0;
  private OutputStream currentOutputStream = null;
  private final DocumentFile dir;
  private final String prefix;
  private final String suffix;
  private final List<DocumentFile> files;
  private final Context context;

  public MultiFileOutputStream29(Context context,
                                 DocumentFile dir,
                                 String prefix,
                                 String suffix) {
    this.context = context;
    this.dir = dir;
    this.prefix = prefix;
    this.files = new ArrayList<>();
    this.suffix  = suffix;
  }

  public void close() throws IOException {
    if (currentOutputStream != null) {
      currentOutputStream.close();
    }
  }

  public void flush() throws IOException {
    if (currentOutputStream != null) {
      currentOutputStream.flush();
    }
  }

  public void write(int b) throws IOException {
    byte[] c = new byte[1];
    c[0] = (byte) b;
    write(c);
  }

  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  private void swapFiles() throws IOException {
    if (currentOutputStream != null) {
      currentOutputStream.close();
    }
    currentChunk++;
    String filename = String.format(Locale.ENGLISH, "%s.%03d%s", prefix, currentChunk, suffix);
    DocumentFile f = dir.createFile("application/octet-stream", filename);
    files.add(f);
    currentOutputStream = Objects.requireNonNull(context.getContentResolver().openOutputStream(f.getUri()));
    bytesRemaining = Util.MAX_BYTES_PER_FILE;
  }

  public void write(byte[] b, int offset, int len) throws IOException {
    if (bytesRemaining == 0) {
      swapFiles();
    }
    int lLen = len;
    int lOffset = offset;
    while (bytesRemaining < lLen) {
      int bytesToWrite = bytesRemaining;
      currentOutputStream.write(b, lOffset, bytesToWrite);
      swapFiles();
      lOffset += bytesToWrite;
      lLen -= bytesToWrite;
    }
    currentOutputStream.write(b, lOffset, lLen);
    bytesRemaining -= lLen;
  }

  public List<DocumentFile> getFiles() {
    return files;
  }
}
