package org.whispersystems.test.ecc;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPrivateKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;

import java.util.Arrays;


public class Curve25519Test extends AndroidTestCase {

  public void testAgreement() throws InvalidKeyException {

    byte[] alicePublic  = {(byte) 0x05, (byte) 0x1b, (byte) 0xb7, (byte) 0x59, (byte) 0x66,
                           (byte) 0xf2, (byte) 0xe9, (byte) 0x3a, (byte) 0x36, (byte) 0x91,
                           (byte) 0xdf, (byte) 0xff, (byte) 0x94, (byte) 0x2b, (byte) 0xb2,
                           (byte) 0xa4, (byte) 0x66, (byte) 0xa1, (byte) 0xc0, (byte) 0x8b,
                           (byte) 0x8d, (byte) 0x78, (byte) 0xca, (byte) 0x3f, (byte) 0x4d,
                           (byte) 0x6d, (byte) 0xf8, (byte) 0xb8, (byte) 0xbf, (byte) 0xa2,
                           (byte) 0xe4, (byte) 0xee, (byte) 0x28};

    byte[] alicePrivate = {(byte) 0xc8, (byte) 0x06, (byte) 0x43, (byte) 0x9d, (byte) 0xc9,
                           (byte) 0xd2, (byte) 0xc4, (byte) 0x76, (byte) 0xff, (byte) 0xed,
                           (byte) 0x8f, (byte) 0x25, (byte) 0x80, (byte) 0xc0, (byte) 0x88,
                           (byte) 0x8d, (byte) 0x58, (byte) 0xab, (byte) 0x40, (byte) 0x6b,
                           (byte) 0xf7, (byte) 0xae, (byte) 0x36, (byte) 0x98, (byte) 0x87,
                           (byte) 0x90, (byte) 0x21, (byte) 0xb9, (byte) 0x6b, (byte) 0xb4,
                           (byte) 0xbf, (byte) 0x59};

    byte[] bobPublic    = {(byte) 0x05, (byte) 0x65, (byte) 0x36, (byte) 0x14, (byte) 0x99,
                           (byte) 0x3d, (byte) 0x2b, (byte) 0x15, (byte) 0xee, (byte) 0x9e,
                           (byte) 0x5f, (byte) 0xd3, (byte) 0xd8, (byte) 0x6c, (byte) 0xe7,
                           (byte) 0x19, (byte) 0xef, (byte) 0x4e, (byte) 0xc1, (byte) 0xda,
                           (byte) 0xae, (byte) 0x18, (byte) 0x86, (byte) 0xa8, (byte) 0x7b,
                           (byte) 0x3f, (byte) 0x5f, (byte) 0xa9, (byte) 0x56, (byte) 0x5a,
                           (byte) 0x27, (byte) 0xa2, (byte) 0x2f};

    byte[] bobPrivate   = {(byte) 0xb0, (byte) 0x3b, (byte) 0x34, (byte) 0xc3, (byte) 0x3a,
                           (byte) 0x1c, (byte) 0x44, (byte) 0xf2, (byte) 0x25, (byte) 0xb6,
                           (byte) 0x62, (byte) 0xd2, (byte) 0xbf, (byte) 0x48, (byte) 0x59,
                           (byte) 0xb8, (byte) 0x13, (byte) 0x54, (byte) 0x11, (byte) 0xfa,
                           (byte) 0x7b, (byte) 0x03, (byte) 0x86, (byte) 0xd4, (byte) 0x5f,
                           (byte) 0xb7, (byte) 0x5d, (byte) 0xc5, (byte) 0xb9, (byte) 0x1b,
                           (byte) 0x44, (byte) 0x66};

    byte[] shared       = {(byte) 0x32, (byte) 0x5f, (byte) 0x23, (byte) 0x93, (byte) 0x28,
                           (byte) 0x94, (byte) 0x1c, (byte) 0xed, (byte) 0x6e, (byte) 0x67,
                           (byte) 0x3b, (byte) 0x86, (byte) 0xba, (byte) 0x41, (byte) 0x01,
                           (byte) 0x74, (byte) 0x48, (byte) 0xe9, (byte) 0x9b, (byte) 0x64,
                           (byte) 0x9a, (byte) 0x9c, (byte) 0x38, (byte) 0x06, (byte) 0xc1,
                           (byte) 0xdd, (byte) 0x7c, (byte) 0xa4, (byte) 0xc4, (byte) 0x77,
                           (byte) 0xe6, (byte) 0x29};

    ECPublicKey alicePublicKey = Curve.decodePoint(alicePublic, 0);
    ECPrivateKey alicePrivateKey = Curve.decodePrivatePoint(alicePrivate);

    ECPublicKey bobPublicKey = Curve.decodePoint(bobPublic, 0);
    ECPrivateKey bobPrivateKey = Curve.decodePrivatePoint(bobPrivate);

    byte[] sharedOne = Curve.calculateAgreement(alicePublicKey, bobPrivateKey);
    byte[] sharedTwo = Curve.calculateAgreement(bobPublicKey, alicePrivateKey);

    assertTrue(Arrays.equals(sharedOne, shared));
    assertTrue(Arrays.equals(sharedTwo, shared));
  }

  public void testRandomAgreements() throws InvalidKeyException {
    for (int i=0;i<50;i++) {
      ECKeyPair alice       = Curve.generateKeyPair();
      ECKeyPair bob         = Curve.generateKeyPair();

      byte[]    sharedAlice = Curve.calculateAgreement(bob.getPublicKey(), alice.getPrivateKey());
      byte[]    sharedBob   = Curve.calculateAgreement(alice.getPublicKey(), bob.getPrivateKey());

      assertTrue(Arrays.equals(sharedAlice, sharedBob));
    }
  }
}
