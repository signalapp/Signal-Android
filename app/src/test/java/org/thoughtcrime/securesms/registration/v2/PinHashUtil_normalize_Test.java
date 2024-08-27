package org.thoughtcrime.securesms.registration.v2;

import org.junit.Test;
import org.signal.core.util.StreamUtil;
import org.thoughtcrime.securesms.registration.testdata.PinSanitationVector;
import org.whispersystems.signalservice.api.kbs.PinHashUtil;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PinHashUtil_normalize_Test {

  @Test
  public void vectors_normalize() throws IOException {
    for (PinSanitationVector vector : getKbsPinSanitationTestVectorList()) {
      byte[] normalized = PinHashUtil.normalize(vector.getPin());

      if (!Arrays.equals(vector.getBytes(), normalized)) {
        assertEquals(String.format("%s [%s]", vector.getName(), vector.getPin()),
                     Hex.toStringCondensed(vector.getBytes()),
                     Hex.toStringCondensed(normalized));
      }
    }
  }

  private static PinSanitationVector[] getKbsPinSanitationTestVectorList() throws IOException {
    try (InputStream resourceAsStream = ClassLoader.getSystemClassLoader().getResourceAsStream("data/kbs_pin_normalization_vectors.json")) {

      PinSanitationVector[] data = JsonUtil.fromJson(StreamUtil.readFullyAsString(resourceAsStream), PinSanitationVector[].class);

      assertTrue(data.length > 0);

      return data;
    }
  }
}
