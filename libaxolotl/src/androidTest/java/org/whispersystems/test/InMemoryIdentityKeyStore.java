package org.whispersystems.test;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class InMemoryIdentityKeyStore implements IdentityKeyStore {

  private final Map<Long, IdentityKey> trustedKeys = new HashMap<>();

  private final IdentityKeyPair identityKeyPair;
  private final int             localRegistrationId;

  public InMemoryIdentityKeyStore() {
    try {
      ECKeyPair identityKeyPairKeys = Curve.generateKeyPair();

      this.identityKeyPair = new IdentityKeyPair(new IdentityKey(identityKeyPairKeys.getPublicKey()),
                                                 identityKeyPairKeys.getPrivateKey());
      this.localRegistrationId = SecureRandom.getInstance("SHA1PRNG").nextInt(16380) + 1;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeyPair;
  }

  @Override
  public int getLocalRegistrationId() {
    return localRegistrationId;
  }

  @Override
  public void saveIdentity(long recipientId, IdentityKey identityKey) {
    trustedKeys.put(recipientId, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(long recipientId, IdentityKey identityKey) {
    IdentityKey trusted = trustedKeys.get(recipientId);
    return (trusted == null || trusted.equals(identityKey));
  }
}
