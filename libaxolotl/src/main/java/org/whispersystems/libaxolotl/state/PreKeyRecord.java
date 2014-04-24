package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.ecc.ECKeyPair;

/**
 * An interface describing a locally stored PreKey.
 *
 * @author Moxie Marlinspike
 */
public interface PreKeyRecord {
  /**
   * @return the PreKey's ID.
   */
  public int       getId();

  /**
   * @return the PreKey's key pair.
   */
  public ECKeyPair getKeyPair();

  /**
   * @return a serialized version of this PreKey.
   */
  public byte[]    serialize();
}
