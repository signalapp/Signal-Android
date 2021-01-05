package org.thoughtcrime.securesms.video.videoconverter.muxer;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Based on https://github.com/jcodec/jcodec/blob/master/src/main/java/org/jcodec/codecs/h264/H264Utils.java
 */
final class Utils {

  private Utils() {}

  static byte[] toArray(final @NonNull ByteBuffer buf) {
    final ByteBuffer newBuf = buf.duplicate();
    byte[]           bytes  = new byte[newBuf.remaining()];
    newBuf.get(bytes, 0, bytes.length);
    return bytes;
  }

  public static ByteBuffer clone(final @NonNull ByteBuffer original) {
    final ByteBuffer clone = ByteBuffer.allocate(original.capacity());
    original.rewind();
    clone.put(original);
    original.rewind();
    clone.flip();
    return clone;
  }

  static @NonNull ByteBuffer subBuffer(final @NonNull ByteBuffer buf, final int start) {
    return subBuffer(buf, start, buf.remaining() - start);
  }

  static @NonNull ByteBuffer subBuffer(final @NonNull ByteBuffer buf, final int start, final int count) {
    final ByteBuffer newBuf = buf.duplicate();
    byte[]           bytes  = new byte[count];
    newBuf.position(start);
    newBuf.get(bytes, 0, bytes.length);
    return ByteBuffer.wrap(bytes);
  }
}
