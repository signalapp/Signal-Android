package org.whispersystems.test.ratchet;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPrivateKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.kdf.HKDF;
import org.whispersystems.libaxolotl.ratchet.ChainKey;
import org.whispersystems.libaxolotl.ratchet.RootKey;
import org.whispersystems.libaxolotl.util.Pair;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class RootKeyTest extends AndroidTestCase {

  public void testRootKeyDerivationV2() throws NoSuchAlgorithmException, InvalidKeyException {
    byte[] rootKeySeed  = {(byte) 0x7b, (byte) 0xa6, (byte) 0xde, (byte) 0xbc, (byte) 0x2b,
                           (byte) 0xc1, (byte) 0xbb, (byte) 0xf9, (byte) 0x1a, (byte) 0xbb,
                           (byte) 0xc1, (byte) 0x36, (byte) 0x74, (byte) 0x04, (byte) 0x17,
                           (byte) 0x6c, (byte) 0xa6, (byte) 0x23, (byte) 0x09, (byte) 0x5b,
                           (byte) 0x7e, (byte) 0xc6, (byte) 0x6b, (byte) 0x45, (byte) 0xf6,
                           (byte) 0x02, (byte) 0xd9, (byte) 0x35, (byte) 0x38, (byte) 0x94,
                           (byte) 0x2d, (byte) 0xcc};

    byte[] alicePublic  = {(byte) 0x05, (byte) 0xee, (byte) 0x4f, (byte) 0xa6, (byte) 0xcd,
                           (byte) 0xc0, (byte) 0x30, (byte) 0xdf, (byte) 0x49, (byte) 0xec,
                           (byte) 0xd0, (byte) 0xba, (byte) 0x6c, (byte) 0xfc, (byte) 0xff,
                           (byte) 0xb2, (byte) 0x33, (byte) 0xd3, (byte) 0x65, (byte) 0xa2,
                           (byte) 0x7f, (byte) 0xad, (byte) 0xbe, (byte) 0xff, (byte) 0x77,
                           (byte) 0xe9, (byte) 0x63, (byte) 0xfc, (byte) 0xb1, (byte) 0x62,
                           (byte) 0x22, (byte) 0xe1, (byte) 0x3a};

    byte[] alicePrivate = {(byte) 0x21, (byte) 0x68, (byte) 0x22, (byte) 0xec, (byte) 0x67,
                           (byte) 0xeb, (byte) 0x38, (byte) 0x04, (byte) 0x9e, (byte) 0xba,
                           (byte) 0xe7, (byte) 0xb9, (byte) 0x39, (byte) 0xba, (byte) 0xea,
                           (byte) 0xeb, (byte) 0xb1, (byte) 0x51, (byte) 0xbb, (byte) 0xb3,
                           (byte) 0x2d, (byte) 0xb8, (byte) 0x0f, (byte) 0xd3, (byte) 0x89,
                           (byte) 0x24, (byte) 0x5a, (byte) 0xc3, (byte) 0x7a, (byte) 0x94,
                           (byte) 0x8e, (byte) 0x50};

    byte[] bobPublic    = {(byte) 0x05, (byte) 0xab, (byte) 0xb8, (byte) 0xeb, (byte) 0x29,
                           (byte) 0xcc, (byte) 0x80, (byte) 0xb4, (byte) 0x71, (byte) 0x09,
                           (byte) 0xa2, (byte) 0x26, (byte) 0x5a, (byte) 0xbe, (byte) 0x97,
                           (byte) 0x98, (byte) 0x48, (byte) 0x54, (byte) 0x06, (byte) 0xe3,
                           (byte) 0x2d, (byte) 0xa2, (byte) 0x68, (byte) 0x93, (byte) 0x4a,
                           (byte) 0x95, (byte) 0x55, (byte) 0xe8, (byte) 0x47, (byte) 0x57,
                           (byte) 0x70, (byte) 0x8a, (byte) 0x30};

    byte[] nextRoot     = {(byte) 0xb1, (byte) 0x14, (byte) 0xf5, (byte) 0xde, (byte) 0x28,
                           (byte) 0x01, (byte) 0x19, (byte) 0x85, (byte) 0xe6, (byte) 0xeb,
                           (byte) 0xa2, (byte) 0x5d, (byte) 0x50, (byte) 0xe7, (byte) 0xec,
                           (byte) 0x41, (byte) 0xa9, (byte) 0xb0, (byte) 0x2f, (byte) 0x56,
                           (byte) 0x93, (byte) 0xc5, (byte) 0xc7, (byte) 0x88, (byte) 0xa6,
                           (byte) 0x3a, (byte) 0x06, (byte) 0xd2, (byte) 0x12, (byte) 0xa2,
                           (byte) 0xf7, (byte) 0x31};

    byte[] nextChain    = {(byte) 0x9d, (byte) 0x7d, (byte) 0x24, (byte) 0x69, (byte) 0xbc,
                           (byte) 0x9a, (byte) 0xe5, (byte) 0x3e, (byte) 0xe9, (byte) 0x80,
                           (byte) 0x5a, (byte) 0xa3, (byte) 0x26, (byte) 0x4d, (byte) 0x24,
                           (byte) 0x99, (byte) 0xa3, (byte) 0xac, (byte) 0xe8, (byte) 0x0f,
                           (byte) 0x4c, (byte) 0xca, (byte) 0xe2, (byte) 0xda, (byte) 0x13,
                           (byte) 0x43, (byte) 0x0c, (byte) 0x5c, (byte) 0x55, (byte) 0xb5,
                           (byte) 0xca, (byte) 0x5f};

    ECPublicKey  alicePublicKey  = Curve.decodePoint(alicePublic, 0);
    ECPrivateKey alicePrivateKey = Curve.decodePrivatePoint(alicePrivate);
    ECKeyPair    aliceKeyPair    = new ECKeyPair(alicePublicKey, alicePrivateKey);

    ECPublicKey bobPublicKey = Curve.decodePoint(bobPublic, 0);
    RootKey     rootKey      = new RootKey(HKDF.createFor(2), rootKeySeed);

    Pair<RootKey, ChainKey> rootKeyChainKeyPair = rootKey.createChain(bobPublicKey, aliceKeyPair);
    RootKey                 nextRootKey         = rootKeyChainKeyPair.first();
    ChainKey                nextChainKey        = rootKeyChainKeyPair.second();

    assertTrue(Arrays.equals(rootKey.getKeyBytes(), rootKeySeed));
    assertTrue(Arrays.equals(nextRootKey.getKeyBytes(), nextRoot));
    assertTrue(Arrays.equals(nextChainKey.getKey(), nextChain));
  }
}
