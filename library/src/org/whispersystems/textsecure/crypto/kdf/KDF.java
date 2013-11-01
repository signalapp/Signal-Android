package org.whispersystems.textsecure.crypto.kdf;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

public abstract class KDF {

  public abstract DerivedSecrets deriveSecrets(List<BigInteger> sharedSecret,
                                               boolean isLowEnd, byte[] info);

  protected byte[] concatenateSharedSecrets(List<BigInteger> sharedSecrets) {
    int          totalByteSize = 0;
    List<byte[]> byteValues    = new LinkedList<byte[]>();

    for (BigInteger sharedSecret : sharedSecrets) {
      byte[] byteValue = sharedSecret.toByteArray();
      totalByteSize += byteValue.length;
      byteValues.add(byteValue);
    }

    byte[] combined = new byte[totalByteSize];
    int offset      = 0;

    for (byte[] byteValue : byteValues) {
      System.arraycopy(byteValue, 0, combined, offset, byteValue.length);
      offset += byteValue.length;
    }

    return combined;
  }

}
