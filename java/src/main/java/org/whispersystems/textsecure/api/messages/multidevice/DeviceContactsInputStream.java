package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;
import org.whispersystems.textsecure.internal.push.TextSecureProtos;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DeviceContactsInputStream {

  private final InputStream in;

  public DeviceContactsInputStream(InputStream in) {
    this.in = in;
  }

  public DeviceContact read() throws IOException {
    long   detailsLength     = readRawVarint64();
    byte[] detailsSerialized = new byte[(int)detailsLength];
    Util.readFully(in, detailsSerialized);

    TextSecureProtos.ContactDetails      details = TextSecureProtos.ContactDetails.parseFrom(detailsSerialized);
    String                               number  = details.getNumber();
    Optional<String>                     name    = Optional.fromNullable(details.getName());
    Optional<TextSecureAttachmentStream> avatar  = Optional.absent();

    if (details.hasAvatar()) {
      long        avatarLength      = details.getAvatar().getLength();
      InputStream avatarStream      = new LimitedInputStream(in, avatarLength);
      String      avatarContentType = details.getAvatar().getContentType();

      avatar = Optional.of(new TextSecureAttachmentStream(avatarStream, avatarContentType, avatarLength));
    }

    return new DeviceContact(number, name, avatar);
  }

  private long readRawVarint64() throws IOException {
    int shift = 0;
    long result = 0;
    while (shift < 64) {
      final byte b = (byte)in.read();
      result |= (long)(b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }

    throw new IOException("Malformed varint!");
  }

  private static final class LimitedInputStream extends FilterInputStream {

    private long left;
    private long mark = -1;

    LimitedInputStream(InputStream in, long limit) {
      super(in);
      left = limit;
    }

    @Override public int available() throws IOException {
      return (int) Math.min(in.available(), left);
    }

    // it's okay to mark even if mark isn't supported, as reset won't work
    @Override public synchronized void mark(int readLimit) {
      in.mark(readLimit);
      mark = left;
    }

    @Override public int read() throws IOException {
      if (left == 0) {
        return -1;
      }

      int result = in.read();
      if (result != -1) {
        --left;
      }
      return result;
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
      if (left == 0) {
        return -1;
      }

      len = (int) Math.min(len, left);
      int result = in.read(b, off, len);
      if (result != -1) {
        left -= result;
      }
      return result;
    }

    @Override public synchronized void reset() throws IOException {
      if (!in.markSupported()) {
        throw new IOException("Mark not supported");
      }
      if (mark == -1) {
        throw new IOException("Mark not set");
      }

      in.reset();
      left = mark;
    }

    @Override public long skip(long n) throws IOException {
      n = Math.min(n, left);
      long skipped = in.skip(n);
      left -= skipped;
      return skipped;
    }
  }

}
