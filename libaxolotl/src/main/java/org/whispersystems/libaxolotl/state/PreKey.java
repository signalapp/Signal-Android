package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;

/**
 * An interface that describes a remote PreKey.
 *
 * @author Moxie Marlinspike
 */
public interface PreKey {
  /**
   * @return the device ID this PreKey belongs to.
   */
  public int         getDeviceId();

  /**
   * @return the unique key ID for this PreKey.
   */
  public int         getKeyId();

  /**
   * @return the public key for this PreKey.
   */
  public ECPublicKey getPublicKey();

  /**
   * @return the {@link org.whispersystems.libaxolotl.IdentityKey} of this PreKeys owner.
   */
  public IdentityKey getIdentityKey();

  /**
   * @return the registration ID associated with this PreKey.
   */
  public int         getRegistrationId();
}
