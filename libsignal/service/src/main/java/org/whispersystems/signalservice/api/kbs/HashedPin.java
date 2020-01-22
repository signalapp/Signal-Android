package org.whispersystems.signalservice.api.kbs;

import org.whispersystems.signalservice.api.crypto.HmacSIV;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;

import static java.util.Arrays.copyOfRange;

/**
 * Represents a hashed pin, from which you can create or decrypt KBS data.
 * <p>
 * Normal Java casing has been ignored to match original specifications.
 */
public final class HashedPin {

  private final byte[] K;
  private final byte[] kbsAccessKey;

  private HashedPin(byte[] K, byte[] kbsAccessKey) {
    this.K            = K;
    this.kbsAccessKey = kbsAccessKey;
  }

  public static HashedPin fromArgon2Hash(byte[] argon2Hash64) {
    if (argon2Hash64.length != 64) throw new AssertionError();
    
    byte[] K            = copyOfRange(argon2Hash64, 0, 32);
    byte[] kbsAccessKey = copyOfRange(argon2Hash64, 32, 64);
    return new HashedPin(K, kbsAccessKey);
  }

  /**
   * Creates a new {@link KbsData} to store on KBS.
   */
  public KbsData createNewKbsData(MasterKey masterKey) {
    byte[] M   = masterKey.serialize();
    byte[] IVC = HmacSIV.encrypt(K, M);
    return new KbsData(masterKey, kbsAccessKey, IVC);
  }

  /**
   * Takes 48 byte IVC from KBS and returns full {@link KbsData}.
   */
  public KbsData decryptKbsDataIVCipherText(byte[] IVC) throws InvalidCiphertextException {
    byte[] masterKey = HmacSIV.decrypt(K, IVC);
    return new KbsData(new MasterKey(masterKey), kbsAccessKey, IVC);
  }

  public byte[] getKbsAccessKey() {
    return kbsAccessKey;
  }
}
