package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;

public interface PreKey {
  public int         getDeviceId();
  public int         getKeyId();
  public ECPublicKey getPublicKey();
  public IdentityKey getIdentityKey();
  public int         getRegistrationId();
}
