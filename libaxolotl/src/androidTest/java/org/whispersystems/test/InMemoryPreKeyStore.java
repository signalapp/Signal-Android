package org.whispersystems.test;

import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;

import java.util.HashMap;
import java.util.Map;

public class InMemoryPreKeyStore implements PreKeyStore {

  private final Map<Integer, PreKeyRecord> store = new HashMap<>();

  @Override
  public PreKeyRecord load(int preKeyId) throws InvalidKeyIdException {
    if (!store.containsKey(preKeyId)) {
      throw new InvalidKeyIdException("No such prekeyrecord!");
    }

    return store.get(preKeyId);
  }

  @Override
  public void store(int preKeyId, PreKeyRecord record) {
    store.put(preKeyId, record);
  }

  @Override
  public boolean contains(int preKeyId) {
    return store.containsKey(preKeyId);
  }

  @Override
  public void remove(int preKeyId) {
    store.remove(preKeyId);
  }
}
