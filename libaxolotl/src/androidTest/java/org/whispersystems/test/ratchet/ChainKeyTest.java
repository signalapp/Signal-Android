package org.whispersystems.test.ratchet;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.kdf.HKDF;
import org.whispersystems.libaxolotl.ratchet.ChainKey;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ChainKeyTest extends AndroidTestCase {

  public void testChainKeyDerivationV2() throws NoSuchAlgorithmException {

    byte[] seed         = {(byte) 0x8a, (byte) 0xb7, (byte) 0x2d, (byte) 0x6f, (byte) 0x4c,
                           (byte) 0xc5, (byte) 0xac, (byte) 0x0d, (byte) 0x38, (byte) 0x7e,
                           (byte) 0xaf, (byte) 0x46, (byte) 0x33, (byte) 0x78, (byte) 0xdd,
                           (byte) 0xb2, (byte) 0x8e, (byte) 0xdd, (byte) 0x07, (byte) 0x38,
                           (byte) 0x5b, (byte) 0x1c, (byte) 0xb0, (byte) 0x12, (byte) 0x50,
                           (byte) 0xc7, (byte) 0x15, (byte) 0x98, (byte) 0x2e, (byte) 0x7a,
                           (byte) 0xd4, (byte) 0x8f};

    byte[] messageKey   = {(byte) 0x02, (byte) 0xa9, (byte) 0xaa, (byte) 0x6c, (byte) 0x7d,
                           (byte) 0xbd, (byte) 0x64, (byte) 0xf9, (byte) 0xd3, (byte) 0xaa,
                           (byte) 0x92, (byte) 0xf9, (byte) 0x2a, (byte) 0x27, (byte) 0x7b,
                           (byte) 0xf5, (byte) 0x46, (byte) 0x09, (byte) 0xda, (byte) 0xdf,
                           (byte) 0x0b, (byte) 0x00, (byte) 0x82, (byte) 0x8a, (byte) 0xcf,
                           (byte) 0xc6, (byte) 0x1e, (byte) 0x3c, (byte) 0x72, (byte) 0x4b,
                           (byte) 0x84, (byte) 0xa7};

    byte[] macKey       = {(byte) 0xbf, (byte) 0xbe, (byte) 0x5e, (byte) 0xfb, (byte) 0x60,
                           (byte) 0x30, (byte) 0x30, (byte) 0x52, (byte) 0x67, (byte) 0x42,
                           (byte) 0xe3, (byte) 0xee, (byte) 0x89, (byte) 0xc7, (byte) 0x02,
                           (byte) 0x4e, (byte) 0x88, (byte) 0x4e, (byte) 0x44, (byte) 0x0f,
                           (byte) 0x1f, (byte) 0xf3, (byte) 0x76, (byte) 0xbb, (byte) 0x23,
                           (byte) 0x17, (byte) 0xb2, (byte) 0xd6, (byte) 0x4d, (byte) 0xeb,
                           (byte) 0x7c, (byte) 0x83};

    byte[] nextChainKey = {(byte) 0x28, (byte) 0xe8, (byte) 0xf8, (byte) 0xfe, (byte) 0xe5,
                           (byte) 0x4b, (byte) 0x80, (byte) 0x1e, (byte) 0xef, (byte) 0x7c,
                           (byte) 0x5c, (byte) 0xfb, (byte) 0x2f, (byte) 0x17, (byte) 0xf3,
                           (byte) 0x2c, (byte) 0x7b, (byte) 0x33, (byte) 0x44, (byte) 0x85,
                           (byte) 0xbb, (byte) 0xb7, (byte) 0x0f, (byte) 0xac, (byte) 0x6e,
                           (byte) 0xc1, (byte) 0x03, (byte) 0x42, (byte) 0xa2, (byte) 0x46,
                           (byte) 0xd1, (byte) 0x5d};

    ChainKey chainKey = new ChainKey(HKDF.createFor(2), seed, 0);

    assertTrue(Arrays.equals(chainKey.getKey(), seed));
    assertTrue(Arrays.equals(chainKey.getMessageKeys().getCipherKey().getEncoded(), messageKey));
    assertTrue(Arrays.equals(chainKey.getMessageKeys().getMacKey().getEncoded(), macKey));
    assertTrue(Arrays.equals(chainKey.getNextChainKey().getKey(), nextChainKey));
    assertTrue(chainKey.getIndex() == 0);
    assertTrue(chainKey.getMessageKeys().getCounter() == 0);
    assertTrue(chainKey.getNextChainKey().getIndex() == 1);
    assertTrue(chainKey.getNextChainKey().getMessageKeys().getCounter() == 1);
  }

}
