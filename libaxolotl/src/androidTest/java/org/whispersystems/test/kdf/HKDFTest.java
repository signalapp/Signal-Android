package org.whispersystems.test.kdf;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.kdf.DerivedSecrets;
import org.whispersystems.libaxolotl.kdf.HKDF;

import java.util.Arrays;

public class HKDFTest extends AndroidTestCase {

  public void testVector() {
    byte[] ikm = {0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
                  0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
                  0x0b, 0x0b};

    byte[] salt = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                   0x0a, 0x0b, 0x0c};

    byte[] info = {(byte)0xf0, (byte)0xf1, (byte)0xf2, (byte)0xf3, (byte)0xf4,
                   (byte)0xf5, (byte)0xf6, (byte)0xf7, (byte)0xf8, (byte)0xf9};

    byte[] expectedOutputOne = {(byte)0x6e, (byte)0xc2, (byte)0x55, (byte)0x6d, (byte)0x5d,
                                (byte)0x7b, (byte)0x1d, (byte)0x81, (byte)0xde, (byte)0xe4,
                                (byte)0x22, (byte)0x2a, (byte)0xd7, (byte)0x48, (byte)0x36,
                                (byte)0x95, (byte)0xdd, (byte)0xc9, (byte)0x8f, (byte)0x4f,
                                (byte)0x5f, (byte)0xab, (byte)0xc0, (byte)0xe0, (byte)0x20,
                                (byte)0x5d, (byte)0xc2, (byte)0xef, (byte)0x87, (byte)0x52,
                                (byte)0xd4, (byte)0x1e};

    byte[] expectedOutputTwo = {(byte)0x04, (byte)0xe2, (byte)0xe2, (byte)0x11, (byte)0x01,
                                (byte)0xc6, (byte)0x8f, (byte)0xf0, (byte)0x93, (byte)0x94,
                                (byte)0xb8, (byte)0xad, (byte)0x0b, (byte)0xdc, (byte)0xb9,
                                (byte)0x60, (byte)0x9c, (byte)0xd4, (byte)0xee, (byte)0x82,
                                (byte)0xac, (byte)0x13, (byte)0x19, (byte)0x9b, (byte)0x4a,
                                (byte)0xa9, (byte)0xfd, (byte)0xa8, (byte)0x99, (byte)0xda,
                                (byte)0xeb, (byte)0xec};

    DerivedSecrets derivedSecrets = new HKDF().deriveSecrets(ikm, salt, info);

    assertTrue(Arrays.equals(derivedSecrets.getCipherKey().getEncoded(), expectedOutputOne));
    assertTrue(Arrays.equals(derivedSecrets.getMacKey().getEncoded(), expectedOutputTwo));
  }
}
