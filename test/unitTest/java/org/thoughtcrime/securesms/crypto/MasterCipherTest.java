package org.thoughtcrime.securesms.crypto;

import org.junit.Before;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.thoughtcrime.securesms.BaseUnitTest;
import org.whispersystems.libsignal.InvalidMessageException;

import javax.crypto.spec.SecretKeySpec;

@PowerMockIgnore("javax.crypto.*")
public class MasterCipherTest extends BaseUnitTest {
  private MasterCipher masterCipher;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    MasterSecret masterSecret = new MasterSecret(new SecretKeySpec(new byte[16], "AES"),
                                                 new SecretKeySpec(new byte[16], "HmacSHA1"));
    masterCipher = new MasterCipher(masterSecret);
  }

  @Test(expected = InvalidMessageException.class)
  public void testEncryptBytesWithZeroBody() throws Exception {
    masterCipher.decryptBytes(new byte[]{});
  }
}
