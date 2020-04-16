package org.whispersystems.signalservice.api.crypto;


import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class DigestingOutputStream extends FilterOutputStream {

  private final MessageDigest runningDigest;

  private byte[] digest;

  public DigestingOutputStream(OutputStream outputStream) {
    super(outputStream);

    try {
      this.runningDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void write(byte[] buffer) throws IOException {
    runningDigest.update(buffer, 0, buffer.length);
    out.write(buffer, 0, buffer.length);
  }

  public void write(byte[] buffer, int offset, int length) throws IOException {
    runningDigest.update(buffer, offset, length);
    out.write(buffer, offset, length);
  }

  public void write(int b) throws IOException {
    runningDigest.update((byte)b);
    out.write(b);
  }

  public void flush() throws IOException {
    digest = runningDigest.digest();
    out.flush();
  }

  public void close() throws IOException {
    out.close();
  }

  public byte[] getTransmittedDigest() {
    return digest;
  }

}
