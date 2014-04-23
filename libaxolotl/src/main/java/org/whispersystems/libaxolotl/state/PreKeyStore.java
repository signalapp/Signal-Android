package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.InvalidKeyIdException;

public interface PreKeyStore {

  public PreKeyRecord load(int preKeyId) throws InvalidKeyIdException;
  public void         store(int preKeyId, PreKeyRecord record);
  public boolean      contains(int preKeyId);
  public void         remove(int preKeyId);

}
