package org.thoughtcrime.securesms.registration.v2;

import org.junit.Test;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.KbsData;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.thoughtcrime.securesms.testutil.SecureRandomTestUtil.mockRandom;

public final class HashedPinKbsDataTest {

  @Test
  public void vectors_createNewKbsData() throws IOException {
    for (KbsTestVector vector : getKbsTestVectorList().getVectors()) {
      HashedPin hashedPin = HashedPin.fromArgon2Hash(vector.getArgon2Hash());

      KbsData kbsData = hashedPin.createNewKbsData(mockRandom(vector.getMasterKey()));

      assertArrayEquals(vector.getMasterKey(), kbsData.getMasterKey().serialize());
      assertArrayEquals(vector.getIvAndCipher(), kbsData.getCipherText());
      assertArrayEquals(vector.getKbsAccessKey(), kbsData.getKbsAccessKey());
      assertEquals(vector.getRegistrationLock(), kbsData.getMasterKey().deriveRegistrationLock());
    }
  }

  @Test
  public void vectors_decryptKbsDataIVCipherText() throws IOException, InvalidCiphertextException {
    for (KbsTestVector vector : getKbsTestVectorList().getVectors()) {
      HashedPin hashedPin = HashedPin.fromArgon2Hash(vector.getArgon2Hash());

      KbsData kbsData = hashedPin.decryptKbsDataIVCipherText(vector.getIvAndCipher());

      assertArrayEquals(vector.getMasterKey(), kbsData.getMasterKey().serialize());
      assertArrayEquals(vector.getIvAndCipher(), kbsData.getCipherText());
      assertArrayEquals(vector.getKbsAccessKey(), kbsData.getKbsAccessKey());
      assertEquals(vector.getRegistrationLock(), kbsData.getMasterKey().deriveRegistrationLock());
    }
  }

  private static KbsTestVectorList getKbsTestVectorList() throws IOException {
    try (InputStream resourceAsStream = ClassLoader.getSystemClassLoader().getResourceAsStream("data/kbs_vectors.json")) {

      KbsTestVectorList data = JsonUtil.fromJson(Util.readFullyAsString(resourceAsStream), KbsTestVectorList.class);

      assertFalse(data.getVectors().isEmpty());

      return data;
    }
  }
}
