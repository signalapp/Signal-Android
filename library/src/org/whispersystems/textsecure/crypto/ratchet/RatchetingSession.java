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
import org.whispersystems.textsecure.storage.SessionState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RatchetingSession {

  public static void initializeSession(SessionState sessionState,
                                       ECKeyPair ourBaseKey,
                                       ECPublicKey theirBaseKey,
                                       ECKeyPair ourEphemeralKey,
                                       ECPublicKey theirEphemeralKey,
                                       IdentityKeyPair ourIdentityKey,
                                       IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    if (isAlice(ourBaseKey.getPublicKey(), theirBaseKey, ourEphemeralKey.getPublicKey(), theirEphemeralKey)) {
      initializeSessionAsAlice(sessionState, ourBaseKey, theirBaseKey, theirEphemeralKey,
                               ourIdentityKey, theirIdentityKey);
    } else {
      initializeSessionAsBob(sessionState, ourBaseKey, theirBaseKey,
                             ourEphemeralKey, ourIdentityKey, theirIdentityKey);
    }
  }

  private static void initializeSessionAsAlice(SessionState sessionState,
                                               ECKeyPair ourBaseKey, ECPublicKey theirBaseKey,
                                               ECPublicKey theirEphemeralKey,
                                               IdentityKeyPair ourIdentityKey,
                                               IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    sessionState.setRemoteIdentityKey(theirIdentityKey);
    sessionState.setLocalIdentityKey(ourIdentityKey.getPublicKey());

    ECKeyPair               sendingKey     = Curve.generateKeyPair(true);
    Pair<RootKey, ChainKey> receivingChain = calculate3DHE(true, ourBaseKey, theirBaseKey, ourIdentityKey, theirIdentityKey);
    Pair<RootKey, ChainKey> sendingChain   = receivingChain.first.createChain(theirEphemeralKey, sendingKey);

    sessionState.addReceiverChain(theirEphemeralKey, receivingChain.second);
    sessionState.setSenderChain(sendingKey, sendingChain.second);
    sessionState.setRootKey(sendingChain.first);
  }

  private static void initializeSessionAsBob(SessionState sessionState,
                                             ECKeyPair ourBaseKey, ECPublicKey theirBaseKey,
                                             ECKeyPair ourEphemeralKey,
                                             IdentityKeyPair ourIdentityKey,
                                             IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    sessionState.setRemoteIdentityKey(theirIdentityKey);
    sessionState.setLocalIdentityKey(ourIdentityKey.getPublicKey());

    Pair<RootKey, ChainKey> sendingChain = calculate3DHE(false, ourBaseKey, theirBaseKey,
                                                         ourIdentityKey, theirIdentityKey);

    sessionState.setSenderChain(ourEphemeralKey, sendingChain.second);
    sessionState.setRootKey(sendingChain.first);
  }

  private static Pair<RootKey, ChainKey> calculate3DHE(boolean isAlice,
                                                       ECKeyPair ourEphemeral, ECPublicKey theirEphemeral,
                                                       IdentityKeyPair ourIdentity, IdentityKey theirIdentity)
      throws InvalidKeyException
  {
    try {
      ByteArrayOutputStream secrets = new ByteArrayOutputStream();

      if (isAlice) {
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
