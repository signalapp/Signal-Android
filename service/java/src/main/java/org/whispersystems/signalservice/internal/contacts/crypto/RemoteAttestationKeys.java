package org.whispersystems.signalservice.internal.contacts.crypto;


import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.libsignal.util.ByteUtil;

public class RemoteAttestationKeys {

  private final byte[] clientKey = new byte[32];
  private final byte[] serverKey = new byte[32];

  public RemoteAttestationKeys(Curve25519KeyPair keyPair, byte[] serverPublicEphemeral, byte[] serverPublicStatic) {
    byte[] ephemeralToEphemeral = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(serverPublicEphemeral, keyPair.getPrivateKey());
    byte[] ephemeralToStatic    = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(serverPublicStatic, keyPair.getPrivateKey());

    byte[] masterSecret = ByteUtil.combine(ephemeralToEphemeral, ephemeralToStatic                          );
    byte[] publicKeys   = ByteUtil.combine(keyPair.getPublicKey(), serverPublicEphemeral, serverPublicStatic);

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
