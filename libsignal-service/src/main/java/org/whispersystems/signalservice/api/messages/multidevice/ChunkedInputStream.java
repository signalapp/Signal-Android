package org.whispersystems.signalservice.api.messages.multidevice;

import java.io.IOException;
import java.io.InputStream;

public class ChunkedInputStream {

  protected final InputStream in;

  public ChunkedInputStream(InputStream in) {
    this.in = in;
  }

  long readRawVarint32() throws IOException {
    long result = 0;
    for (int shift = 0; shift < 32; shift += 7) {
      int tmpInt = in.read();
      if (tmpInt < 0) {
        return -1;
      }
      final byte b = (byte) tmpInt;
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
    }
    throw new IOException("Malformed varint!");
  }
}
