package org.thoughtcrime.securesms.registration.v2;

import org.junit.Test;
import org.signal.core.util.StreamUtil;
import org.signal.libsignal.svr2.PinHash;
import org.thoughtcrime.securesms.registration.testdata.KbsTestVector;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.kbs.KbsData;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.kbs.PinHashUtil;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.thoughtcrime.securesms.testutil.SecureRandomTestUtil.mockRandom;

public final class PinHashKbsDataTest {

  @Test
  public void vectors_createNewKbsData() throws IOException {
    for (KbsTestVector vector : getKbsTestVectorList()) {
      PinHash pinHash = fromArgon2Hash(vector.getArgon2Hash());

      KbsData kbsData = PinHashUtil.createNewKbsData(pinHash, MasterKey.createNew(mockRandom(vector.getMasterKey())));

      assertArrayEquals(vector.getMasterKey(), kbsData.getMasterKey().serialize());
      assertArrayEquals(vector.getIvAndCipher(), kbsData.getCipherText());
      assertArrayEquals(vector.getKbsAccessKey(), kbsData.getKbsAccessKey());
      assertEquals(vector.getRegistrationLock(), kbsData.getMasterKey().deriveRegistrationLock());
    }
  }

  @Test
  public void vectors_decryptKbsDataIVCipherText() throws IOException, InvalidCiphertextException {
    for (KbsTestVector vector : getKbsTestVectorList()) {
      PinHash hashedPin = fromArgon2Hash(vector.getArgon2Hash());

      KbsData kbsData = PinHashUtil.decryptSvrDataIVCipherText(hashedPin, vector.getIvAndCipher());

      assertArrayEquals(vector.getMasterKey(), kbsData.getMasterKey().serialize());
      assertArrayEquals(vector.getIvAndCipher(), kbsData.getCipherText());
      assertArrayEquals(vector.getKbsAccessKey(), kbsData.getKbsAccessKey());
      assertEquals(vector.getRegistrationLock(), kbsData.getMasterKey().deriveRegistrationLock());
    }
  }

  private static KbsTestVector[] getKbsTestVectorList() throws IOException {
    try (InputStream resourceAsStream = ClassLoader.getSystemClassLoader().getResourceAsStream("data/kbs_vectors.json")) {

      KbsTestVector[] data = JsonUtil.fromJson(StreamUtil.readFullyAsString(resourceAsStream), KbsTestVector[].class);

      assertTrue(data.length > 0);

      return data;
    }
  }

  public static PinHash fromArgon2Hash(byte[] argon2Hash64) {
    if (argon2Hash64.length != 64) throw new AssertionError();

    byte[] K            = Arrays.copyOfRange(argon2Hash64, 0, 32);
    byte[] kbsAccessKey = Arrays.copyOfRange(argon2Hash64, 32, 64);

    PinHash mocked = mock(PinHash.class);
    when(mocked.encryptionKey()).thenReturn(K);
    when(mocked.accessKey()).thenReturn(kbsAccessKey);

    return mocked;
  }
}
