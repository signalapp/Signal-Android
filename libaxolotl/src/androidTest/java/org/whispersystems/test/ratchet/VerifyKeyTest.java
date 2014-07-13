package org.whispersystems.test.ratchet;

import android.test.AndroidTestCase;
import android.util.Log;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.ratchet.VerifyKey;
import org.whispersystems.libaxolotl.util.Hex;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class VerifyKeyTest extends AndroidTestCase {

  public void testVerify() throws NoSuchAlgorithmException, InvalidKeyException {
    byte[] aliceBaseKeyBytes     = {(byte) 0x05, (byte) 0x2d, (byte) 0x0c, (byte) 0xdd, (byte) 0xde,
                                    (byte) 0xa8, (byte) 0x9f, (byte) 0x6a, (byte) 0x2c, (byte) 0xe0,
                                    (byte) 0x21, (byte) 0xfa, (byte) 0x69, (byte) 0x39, (byte) 0x30,
                                    (byte) 0x43, (byte) 0x28, (byte) 0xd0, (byte) 0xa3, (byte) 0x53,
                                    (byte) 0xe0, (byte) 0x67, (byte) 0xb9, (byte) 0x11, (byte) 0xf5,
                                    (byte) 0xa9, (byte) 0xbd, (byte) 0xa4, (byte) 0x7b, (byte) 0x29,
                                    (byte) 0x41, (byte) 0x6e, (byte) 0x2b};

    byte[] aliceIdentityKeyBytes = {(byte) 0x05, (byte) 0x9d, (byte) 0x86, (byte) 0xef, (byte) 0x77,
                                    (byte) 0x7d, (byte) 0x71, (byte) 0x0c, (byte) 0xc2, (byte) 0xb1,
                                    (byte) 0x4e, (byte) 0xd6, (byte) 0x15, (byte) 0x2e, (byte) 0x91,
                                    (byte) 0xfb, (byte) 0x7f, (byte) 0xa2, (byte) 0x34, (byte) 0xe5,
                                    (byte) 0x5b, (byte) 0x57, (byte) 0x2e, (byte) 0x52, (byte) 0xb8,
                                    (byte) 0x5f, (byte) 0x84, (byte) 0xdb, (byte) 0x34, (byte) 0x16,
                                    (byte) 0x69, (byte) 0xfd, (byte) 0x45};

    byte[] bobBaseKeyBytes       = {(byte) 0x05, (byte) 0xc0, (byte) 0xbd, (byte) 0x26, (byte) 0x62,
                                    (byte) 0xf7, (byte) 0xea, (byte) 0xa8, (byte) 0x5a, (byte) 0x5e,
                                    (byte) 0x43, (byte) 0x95, (byte) 0x34, (byte) 0x3a, (byte) 0xcf,
                                    (byte) 0x66, (byte) 0x36, (byte) 0xec, (byte) 0x75, (byte) 0x54,
                                    (byte) 0x7b, (byte) 0x96, (byte) 0x02, (byte) 0x6d, (byte) 0x8a,
                                    (byte) 0x16, (byte) 0xb6, (byte) 0x39, (byte) 0x10, (byte) 0x36,
                                    (byte) 0xf6, (byte) 0x9f, (byte) 0x39};

    byte[] bobPreKeyBytes        = {(byte) 0x05, (byte) 0xb8, (byte) 0x28, (byte) 0x04, (byte) 0xe6,
                                    (byte) 0x46, (byte) 0xeb, (byte) 0x04, (byte) 0xaf, (byte) 0x54,
                                    (byte) 0xeb, (byte) 0xea, (byte) 0xfa, (byte) 0x8e, (byte) 0x27,
                                    (byte) 0xb1, (byte) 0xa7, (byte) 0xa8, (byte) 0x00, (byte) 0xef,
                                    (byte) 0xcf, (byte) 0xd7, (byte) 0xe8, (byte) 0x9c, (byte) 0x92,
                                    (byte) 0xfc, (byte) 0x51, (byte) 0x66, (byte) 0xb8, (byte) 0x70,
                                    (byte) 0xee, (byte) 0x63, (byte) 0x74};

    byte[] bobIdentityKeyBytes   = {(byte) 0x05, (byte) 0x3a, (byte) 0x32, (byte) 0x3a, (byte) 0xda,
                                    (byte) 0xe8, (byte) 0x46, (byte) 0x1b, (byte) 0x57, (byte) 0x8d,
                                    (byte) 0x46, (byte) 0x70, (byte) 0x80, (byte) 0x0e, (byte) 0x06,
                                    (byte) 0x76, (byte) 0x5a, (byte) 0xf1, (byte) 0x50, (byte) 0x51,
                                    (byte) 0xd3, (byte) 0x74, (byte) 0xa0, (byte) 0x65, (byte) 0x85,
                                    (byte) 0xea, (byte) 0x03, (byte) 0xff, (byte) 0x58, (byte) 0x7c,
                                    (byte) 0x81, (byte) 0xa8, (byte) 0x04};


    byte[] key = {(byte)0xfc, (byte)0x57, (byte)0x05, (byte)0xdc, (byte)0xe0,
                  (byte)0x34, (byte)0x4c, (byte)0x8f, (byte)0x1c, (byte)0xeb,
                  (byte)0x9b, (byte)0x05, (byte)0x7c, (byte)0xaa, (byte)0xb0,
                  (byte)0x08, (byte)0xf0, (byte)0xb7, (byte)0x26, (byte)0x73,
                  (byte)0x46, (byte)0xa4, (byte)0x00, (byte)0xa3, (byte)0x66,
                  (byte)0x79, (byte)0x00, (byte)0xef, (byte)0x1b, (byte)0x40,
                  (byte)0x0f, (byte)0xdc};

    byte[] expectedTag = {(byte)0x2f, (byte)0x77, (byte)0xaf, (byte)0xad, (byte)0x5b,
                          (byte)0x96, (byte)0xf5, (byte)0x3c};

    ECPublicKey aliceBaseKey     = Curve.decodePoint(aliceBaseKeyBytes, 0);
    ECPublicKey alicePreKey      = aliceBaseKey;
    ECPublicKey aliceIdentityKey = Curve.decodePoint(aliceIdentityKeyBytes, 0);

    ECPublicKey bobBaseKey       = Curve.decodePoint(bobBaseKeyBytes, 0);
    ECPublicKey bobPreKey        = Curve.decodePoint(bobPreKeyBytes, 0);
    ECPublicKey bobIdentityKey   = Curve.decodePoint(bobIdentityKeyBytes, 0);

    VerifyKey verifyKey    = new VerifyKey(key);
    byte[]    verification = verifyKey.generateVerification(aliceBaseKey, Optional.of(alicePreKey),
                                                            aliceIdentityKey, bobBaseKey,
                                                            Optional.of(bobPreKey), bobIdentityKey);

    assertTrue(MessageDigest.isEqual(verification, expectedTag));
  }

}
