package org.whispersystems.signalservice.internal.contacts.crypto;


import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.libsignal.util.ByteUtil;

public class RemoteAttestationKeys {

  private final byte[] clientKey = new byte[32];
  private final byte[] serverKey = new byte[32];

  public RemoteAttestationKeys(ECKeyPair keyPair, byte[] serverPublicEphemeral, byte[] serverPublicStatic) throws InvalidKeyException {
    byte[] ephemeralToEphemeral = Curve.calculateAgreement(ECPublicKey.fromPublicKeyBytes(serverPublicEphemeral), keyPair.getPrivateKey());
    byte[] ephemeralToStatic    = Curve.calculateAgreement(ECPublicKey.fromPublicKeyBytes(serverPublicStatic), keyPair.getPrivateKey());

    byte[] masterSecret = ByteUtil.combine(ephemeralToEphemeral, ephemeralToStatic                          );
    byte[] publicKeys   = ByteUtil.combine(keyPair.getPublicKey().getPublicKeyBytes(), serverPublicEphemeral, serverPublicStatic);

    HKDFv3 generator = new HKDFv3();
    byte[] keys      = generator.deriveSecrets(masterSecret, publicKeys, null, clientKey.length + serverKey.length);

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
