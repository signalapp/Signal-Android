package org.whispersystems.signalservice.api.crypto;

import java.io.OutputStream;

/**
 * Use when the stream is already encrypted.
 */
public final class NoCipherOutputStream extends DigestingOutputStream {

  public NoCipherOutputStream(OutputStream outputStream) {
    super(outputStream);
  }
}
