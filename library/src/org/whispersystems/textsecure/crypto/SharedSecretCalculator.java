package org.whispersystems.textsecure.crypto;

import android.util.Log;

import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.agreement.ECDHBasicAgreement;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.kdf.HKDF;
import org.whispersystems.textsecure.crypto.kdf.KDF;
import org.whispersystems.textsecure.crypto.kdf.NKDF;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.util.Conversions;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

public class SharedSecretCalculator {

  public static DerivedSecrets calculateSharedSecret(boolean isLowEnd, KeyPair localKeyPair,
                                                     int localKeyId,
                                                     IdentityKeyPair localIdentityKeyPair,
                                                     ECPublicKeyParameters remoteKey,
                                                     int remoteKeyId,
                                                     IdentityKey remoteIdentityKey)
  {
    Log.w("SharedSecretCalculator", "Calculating shared secret with cradle agreement...");
    KDF              kdf     = new HKDF();
    List<BigInteger> results = new LinkedList<BigInteger>();

    if (isSmaller(localKeyPair.getPublicKey().getKey(), remoteKey)) {
      results.add(calculateAgreement(localIdentityKeyPair.getPrivateKey(), remoteKey));

      results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(),
                                     remoteIdentityKey.getPublicKeyParameters()));
    } else {
      results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(),
                                     remoteIdentityKey.getPublicKeyParameters()));

      results.add(calculateAgreement(localIdentityKeyPair.getPrivateKey(), remoteKey));
    }

    results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(), remoteKey));

    return kdf.deriveSecrets(results, isLowEnd, getInfo(localKeyId,remoteKeyId));
  }

  public static DerivedSecrets calculateSharedSecret(int messageVersion, boolean isLowEnd,
                                                     KeyPair localKeyPair, int localKeyId,
                                                     ECPublicKeyParameters remoteKey, int remoteKeyId)
  {
    Log.w("SharedSecretCalculator", "Calculating shared secret with standard agreement...");
    KDF kdf;

    if (messageVersion >= CiphertextMessage.DHE3_INTRODUCED_VERSION) kdf = new HKDF();
    else                                                             kdf = new NKDF();

    Log.w("SharedSecretCalculator", "Using kdf:  " + kdf);

    List<BigInteger> results = new LinkedList<BigInteger>();
    results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(), remoteKey));

    return kdf.deriveSecrets(results, isLowEnd, getInfo(localKeyId, remoteKeyId));
  }

  private static byte[] getInfo(int localKeyId, int remoteKeyId) {
    byte[] info = new byte[3 * 2];

    if (localKeyId < remoteKeyId) {
      Conversions.mediumToByteArray(info, 0, localKeyId);
      Conversions.mediumToByteArray(info, 3, remoteKeyId);
    } else {
      Conversions.mediumToByteArray(info, 0, remoteKeyId);
      Conversions.mediumToByteArray(info, 3, localKeyId);
    }

    return info;
  }

  private static BigInteger calculateAgreement(CipherParameters privateKey,
                                               ECPublicKeyParameters publicKey)
  {
    ECDHBasicAgreement agreement = new ECDHBasicAgreement();
    agreement.init(privateKey);

    return KeyUtil.calculateAgreement(agreement, publicKey);
  }


  private static boolean isSmaller(ECPublicKeyParameters localPublic,
                                   ECPublicKeyParameters remotePublic)
  {
    BigInteger local  = localPublic.getQ().getX().toBigInteger();
    BigInteger remote = remotePublic.getQ().getX().toBigInteger();

    return local.compareTo(remote) < 0;
  }

}
