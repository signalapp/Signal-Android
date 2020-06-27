package org.whispersystems.signalservice.internal.push;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import okio.Source;
import okio.Timeout;

/**
 * NowhereBufferedSync allows a programmer to write out data into the void. This has no memory
 * implications, as we don't actually store bytes. Supports getting an OutputStream, which also
 * just writes into the void.
 */
public class NowhereBufferedSink implements BufferedSink {
  @Override
  public Buffer buffer() {
    return null;
  }

  @Override
  public BufferedSink write(ByteString byteString) throws IOException {
    return this;
  }

  @Override
  public BufferedSink write(byte[] source) throws IOException {
    return this;
  }

  @Override
  public BufferedSink write(byte[] source, int offset, int byteCount) throws IOException {
    return this;
  }

  @Override
  public long writeAll(Source source) throws IOException {
    return 0;
  }

  @Override
  public BufferedSink write(Source source, long byteCount) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeUtf8(String string) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeUtf8(String string, int beginIndex, int endIndex) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeUtf8CodePoint(int codePoint) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeString(String string, Charset charset) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeString(String string, int beginIndex, int endIndex, Charset charset) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeByte(int b) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeShort(int s) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeShortLe(int s) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeInt(int i) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeIntLe(int i) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeLong(long v) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeLongLe(long v) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeDecimalLong(long v) throws IOException {
    return this;
  }

  @Override
  public BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException {
    return this;
  }

  @Override
  public void write(Buffer source, long byteCount) throws IOException {

  }

  @Override
  public void flush() throws IOException {

  }

  @Override
  public Timeout timeout() {
    return null;
  }

  @Override
  public void close() throws IOException {

  }

  @Override
  public BufferedSink emit() throws IOException {
    return this;
  }

  @Override
  public BufferedSink emitCompleteSegments() throws IOException {
    return this;
  }

  @Override
  public OutputStream outputStream() {
    return new OutputStream() {
      @Override
      public void write(int i) throws IOException {
      }

      @Override
      public void write(byte[] bytes) throws IOException {
      }

      @Override
      public void write(byte[] bytes, int i, int i1) throws IOException {
      }
    };
  }

  @Override
  public int write(ByteBuffer byteBuffer) throws IOException {
    return 0;
  }

  @Override
  public boolean isOpen() {
    return false;
  }
}
