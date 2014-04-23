package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.ecc.ECKeyPair;

public interface PreKeyRecord {
  public int       getId();
  public ECKeyPair getKeyPair();
  public byte[]    serialize();
}
