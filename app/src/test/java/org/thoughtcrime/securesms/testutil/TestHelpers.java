package org.thoughtcrime.securesms.testutil;

import com.annimon.stream.Stream;
import com.google.common.collect.Sets;

import org.thoughtcrime.securesms.util.Conversions;
import org.whispersystems.libsignal.util.ByteUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public final class TestHelpers {

  private TestHelpers() {}


  public static byte[] byteArray(int a) {
    return Conversions.intToByteArray(a);
  }

  public static byte[] byteArray(int a, int totalLength) {
    byte[] out = new byte[totalLength - 4];
    byte[] val = Conversions.intToByteArray(a);
    return ByteUtil.combine(out, val);
  }

  public static List<byte[]> byteListOf(int... vals) {
    List<byte[]> list = new ArrayList<>(vals.length);

    for (int i = 0; i < vals.length; i++) {
      list.add(Conversions.intToByteArray(vals[i]));

    }
    return list;
  }

  @SafeVarargs
  public static <E> Set<E> setOf(E... values) {
    return Sets.newHashSet(values);
  }

  public static void assertByteListEquals(List<byte[]> a, List<byte[]> b) {
    assertEquals(a.size(), b.size());

    List<ByteBuffer> aBuffer = Stream.of(a).map(ByteBuffer::wrap).toList();
    List<ByteBuffer> bBuffer = Stream.of(b).map(ByteBuffer::wrap).toList();

    assertTrue(aBuffer.containsAll(bBuffer));
  }

  public static <E> void assertContentsEqual(Collection<E> a, Collection<E> b) {
    assertEquals(a.size(), b.size());
    assertTrue(a.containsAll(b));
  }
}
