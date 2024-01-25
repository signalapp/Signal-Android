package org.whispersystems.signalservice.api.crypto;

import junit.framework.TestCase;

import org.conscrypt.Conscrypt;
import org.conscrypt.OpenSSLProvider;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;

import java.security.Security;
import java.util.Arrays;

public class UnidentifiedAccessTest extends TestCase {

  static {
    // https://github.com/google/conscrypt/issues/1034
    if (!System.getProperty("os.arch").equals("aarch64")) {
      Security.insertProviderAt(new OpenSSLProvider(), 1);
    }
  }

  private final byte[] EXPECTED_RESULT = {(byte)0x5a, (byte)0x72, (byte)0x3a, (byte)0xce, (byte)0xe5, (byte)0x2c, (byte)0x5e, (byte)0xa0, (byte)0x2b, (byte)0x92, (byte)0xa3, (byte)0xa3, (byte)0x60, (byte)0xc0, (byte)0x95, (byte)0x95};

  public void testKeyDerivation() throws InvalidInputException {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte)0x02);

    byte[] result = UnidentifiedAccess.deriveAccessKeyFrom(new ProfileKey(key));
    assertTrue(Arrays.equals(result, EXPECTED_RESULT));
  }

}
