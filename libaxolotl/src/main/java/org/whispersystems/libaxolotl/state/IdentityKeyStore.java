package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;

public interface IdentityKeyStore {

  public IdentityKeyPair getIdentityKeyPair();
  public int             getLocalRegistrationId();
  public void            saveIdentity(long recipientId, IdentityKey identityKey);

}
