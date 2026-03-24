package org.whispersystems.signalservice.api.messages.multidevice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ChunkedOutputStream {

  protected final OutputStream out;

  public ChunkedOutputStream(OutputStream out) {
    this.out = out;
  }

  protected void writeVarint32(int value) throws IOException {
    while (true) {
      if ((value & ~0x7F) == 0) {
        out.write(value);
        return;
      } else {
        out.write((value & 0x7F) | 0x80);
        value >>>= 7;
      }
    }
  }

  protected void writeStream(InputStream in) throws IOException {
    byte[] buffer = new byte[4096];
    int read;

    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }

    in.close();
  }

}
