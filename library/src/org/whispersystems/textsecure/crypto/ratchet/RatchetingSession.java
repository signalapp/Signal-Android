package org.whispersystems.textsecure.crypto.ratchet;

import android.util.Pair;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.kdf.HKDF;
import org.whispersystems.textsecure.storage.SessionRecordV2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RatchetingSession {

  public static void initializeSession(SessionRecordV2 sessionRecord,
                                       ECKeyPair ourBaseKey,
                                       ECPublicKey theirBaseKey,
                                       ECKeyPair ourEphemeralKey,
                                       ECPublicKey theirEphemeralKey,
                                       IdentityKeyPair ourIdentityKey,
                                       IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    if (isAlice(ourBaseKey.getPublicKey(), theirBaseKey, ourEphemeralKey.getPublicKey(), theirEphemeralKey)) {
      initializeSessionAsAlice(sessionRecord, ourBaseKey, theirBaseKey, theirEphemeralKey,
                               ourIdentityKey, theirIdentityKey);
    } else {
      initializeSessionAsBob(sessionRecord, ourBaseKey, theirBaseKey,
                             ourEphemeralKey, ourIdentityKey, theirIdentityKey);
    }
  }

  private static void initializeSessionAsAlice(SessionRecordV2 sessionRecord,
                                               ECKeyPair ourBaseKey, ECPublicKey theirBaseKey,
                                               ECPublicKey theirEphemeralKey,
                                               IdentityKeyPair ourIdentityKey,
                                               IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    sessionRecord.setRemoteIdentityKey(theirIdentityKey);
    sessionRecord.setLocalIdentityKey(ourIdentityKey.getPublicKey());

    ECKeyPair               sendingKey     = Curve.generateKeyPairForType(ourIdentityKey.getPublicKey().getPublicKey().getType());
    Pair<RootKey, ChainKey> receivingChain = calculate3DHE(ourBaseKey, theirBaseKey, ourIdentityKey, theirIdentityKey);
    Pair<RootKey, ChainKey> sendingChain   = receivingChain.first.createChain(theirEphemeralKey, sendingKey);

    sessionRecord.addReceiverChain(theirEphemeralKey, receivingChain.second);
    sessionRecord.setSenderChain(sendingKey, sendingChain.second);
    sessionRecord.setRootKey(sendingChain.first);
  }

  private static void initializeSessionAsBob(SessionRecordV2 sessionRecord,
                                             ECKeyPair ourBaseKey, ECPublicKey theirBaseKey,
                                             ECKeyPair ourEphemeralKey,
                                             IdentityKeyPair ourIdentityKey,
                                             IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    sessionRecord.setRemoteIdentityKey(theirIdentityKey);
    sessionRecord.setLocalIdentityKey(ourIdentityKey.getPublicKey());

    Pair<RootKey, ChainKey> sendingChain = calculate3DHE(ourBaseKey, theirBaseKey,
                                                         ourIdentityKey, theirIdentityKey);

    sessionRecord.setSenderChain(ourEphemeralKey, sendingChain.second);
    sessionRecord.setRootKey(sendingChain.first);
  }

  private static Pair<RootKey, ChainKey> calculate3DHE(ECKeyPair ourEphemeral, ECPublicKey theirEphemeral,
                                                       IdentityKeyPair ourIdentity, IdentityKey theirIdentity)
      throws InvalidKeyException
  {
    try {
      ByteArrayOutputStream secrets = new ByteArrayOutputStream();

      if (isLowEnd(ourEphemeral.getPublicKey(), theirEphemeral)) {
        secrets.write(Curve.calculateAgreement(theirEphemeral, ourIdentity.getPrivateKey()));
        secrets.write(Curve.calculateAgreement(theirIdentity.getPublicKey(), ourEphemeral.getPrivateKey()));
      } else {
        secrets.write(Curve.calculateAgreement(theirIdentity.getPublicKey(), ourEphemeral.getPrivateKey()));
        secrets.write(Curve.calculateAgreement(theirEphemeral, ourIdentity.getPrivateKey()));
      }

      secrets.write(Curve.calculateAgreement(theirEphemeral, ourEphemeral.getPrivateKey()));

      DerivedSecrets derivedSecrets = new HKDF().deriveSecrets(secrets.toByteArray(),
                                                               "WhisperText".getBytes());

      return new Pair<RootKey, ChainKey>(new RootKey(derivedSecrets.getCipherKey().getEncoded()),
                                         new ChainKey(derivedSecrets.getMacKey().getEncoded(), 0));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static boolean isAlice(ECPublicKey ourBaseKey, ECPublicKey theirBaseKey,
                                 ECPublicKey ourEphemeralKey, ECPublicKey theirEphemeralKey)
  {
    if (ourEphemeralKey.equals(ourBaseKey)) {
      return false;
    }

    if (theirEphemeralKey.equals(theirBaseKey)) {
      return true;
    }

    return isLowEnd(ourBaseKey, theirBaseKey);
  }

  private static boolean isLowEnd(ECPublicKey ourKey, ECPublicKey theirKey) {
    return ourKey.compareTo(theirKey) < 0;
  }


}
