package org.thoughtcrime.securesms.registration.v2;

import org.junit.Test;
import org.signal.core.util.StreamUtil;
import org.thoughtcrime.securesms.registration.testdata.PinValidityVector;
import org.whispersystems.signalservice.api.kbs.PinValidityChecker;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PinValidityChecker_validity_Test {

  @Test
  public void vectors_valid() throws IOException {
    for (PinValidityVector vector : getKbsPinValidityTestVectorList()) {
      boolean valid = PinValidityChecker.valid(vector.getPin());

      assertEquals(String.format("%s [%s]", vector.getName(), vector.getPin()),
                   vector.isValid(),
                   valid);
    }
  }

  private static PinValidityVector[] getKbsPinValidityTestVectorList() throws IOException {
    try (InputStream resourceAsStream = ClassLoader.getSystemClassLoader().getResourceAsStream("data/kbs_pin_validity_vectors.json")) {

      PinValidityVector[] data = JsonUtil.fromJson(StreamUtil.readFullyAsString(resourceAsStream), PinValidityVector[].class);

      assertTrue(data.length > 0);

      return data;
    }
  }
}
