package org.whispersystems.signalservice.internal.contacts.crypto;


import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kdf.HKDF;
import org.signal.libsignal.protocol.util.ByteUtil;

public class RemoteAttestationKeys {

  private final byte[] clientKey = new byte[32];
  private final byte[] serverKey = new byte[32];

  public RemoteAttestationKeys(ECKeyPair keyPair, byte[] serverPublicEphemeral, byte[] serverPublicStatic) throws InvalidKeyException {
    byte[] ephemeralToEphemeral = Curve.calculateAgreement(ECPublicKey.fromPublicKeyBytes(serverPublicEphemeral), keyPair.getPrivateKey());
    byte[] ephemeralToStatic    = Curve.calculateAgreement(ECPublicKey.fromPublicKeyBytes(serverPublicStatic), keyPair.getPrivateKey());

    byte[] masterSecret = ByteUtil.combine(ephemeralToEphemeral, ephemeralToStatic                          );
    byte[] publicKeys   = ByteUtil.combine(keyPair.getPublicKey().getPublicKeyBytes(), serverPublicEphemeral, serverPublicStatic);

    byte[] keys      = HKDF.deriveSecrets(masterSecret, publicKeys, new byte[0], clientKey.length + serverKey.length);

    System.arraycopy(keys, 0, clientKey, 0, clientKey.length);
    System.arraycopy(keys, clientKey.length, serverKey, 0, serverKey.length);
  }

  public byte[] getClientKey() {
    return clientKey;
  }

  public byte[] getServerKey() {
    return serverKey;
  }
}
