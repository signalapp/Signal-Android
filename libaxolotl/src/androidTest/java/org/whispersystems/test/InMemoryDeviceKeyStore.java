package org.whispersystems.test;

import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.state.DeviceKeyRecord;
import org.whispersystems.libaxolotl.state.DeviceKeyStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class InMemoryDeviceKeyStore implements DeviceKeyStore {

  private final Map<Integer, byte[]> store = new HashMap<>();

  @Override
  public DeviceKeyRecord loadDeviceKey(int deviceKeyId) throws InvalidKeyIdException {
    try {
      if (!store.containsKey(deviceKeyId)) {
        throw new InvalidKeyIdException("No such devicekeyrecord!");
      }

      return new DeviceKeyRecord(store.get(deviceKeyId));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public List<DeviceKeyRecord> loadDeviceKeys() {
    try {
      List<DeviceKeyRecord> results = new LinkedList<>();

      for (byte[] serialized : store.values()) {
        results.add(new DeviceKeyRecord(serialized));
      }

      return results;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void storeDeviceKey(int deviceKeyId, DeviceKeyRecord record) {
    store.put(deviceKeyId, record.serialize());
  }

  @Override
  public boolean containsDeviceKey(int deviceKeyId) {
    return store.containsKey(deviceKeyId);
  }

  @Override
  public void removeDeviceKey(int deviceKeyId) {
    store.remove(deviceKeyId);
  }
}
