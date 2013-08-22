package org.whispersystems.textsecure.crypto;

import android.util.Log;

import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.agreement.ECDHBasicAgreement;
import org.spongycastle.crypto.params.ECPublicKeyParameters;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

public class SharedSecretCalculator {

  public static List<BigInteger> calculateSharedSecret(KeyPair localKeyPair,
                                                       IdentityKeyPair localIdentityKeyPair,
                                                       ECPublicKeyParameters remoteKey,
                                                       IdentityKey remoteIdentityKey)
  {
    Log.w("SharedSecretCalculator", "Calculating shared secret with cradle agreement...");
    List<BigInteger> results = new LinkedList<BigInteger>();

    if (isLowEnd(localKeyPair.getPublicKey().getKey(), remoteKey)) {
      results.add(calculateAgreement(localIdentityKeyPair.getPrivateKey(), remoteKey));

      results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(),
                                     remoteIdentityKey.getPublicKeyParameters()));
    } else {
      results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(),
                                     remoteIdentityKey.getPublicKeyParameters()));

      results.add(calculateAgreement(localIdentityKeyPair.getPrivateKey(), remoteKey));
    }

    results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(), remoteKey));
    return results;
  }

  public static List<BigInteger> calculateSharedSecret(KeyPair localKeyPair,
                                                       ECPublicKeyParameters remoteKey)
  {
    Log.w("SharedSecretCalculator", "Calculating shared secret with standard agreement...");
    List<BigInteger> results = new LinkedList<BigInteger>();
    results.add(calculateAgreement(localKeyPair.getKeyPair().getPrivate(), remoteKey));

    return results;
  }

  private static BigInteger calculateAgreement(CipherParameters privateKey,
                                               ECPublicKeyParameters publicKey)
  {
    ECDHBasicAgreement agreement = new ECDHBasicAgreement();
    agreement.init(privateKey);

    return KeyUtil.calculateAgreement(agreement, publicKey);
  }


  private static boolean isLowEnd(ECPublicKeyParameters localPublic,
                                  ECPublicKeyParameters remotePublic)
  {
    BigInteger local  = localPublic.getQ().getX().toBigInteger();
    BigInteger remote = remotePublic.getQ().getX().toBigInteger();

    return local.compareTo(remote) < 0;
  }

}
