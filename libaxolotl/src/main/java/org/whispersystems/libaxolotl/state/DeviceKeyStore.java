package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.InvalidKeyIdException;

import java.util.List;

public interface DeviceKeyStore {


  /**
   * Load a local DeviceKeyRecord.
   *
   * @param deviceKeyId the ID of the local DeviceKeyRecord.
   * @return the corresponding DeviceKeyRecord.
   * @throws InvalidKeyIdException when there is no corresponding DeviceKeyRecord.
   */
  public DeviceKeyRecord loadDeviceKey(int deviceKeyId) throws InvalidKeyIdException;

  /**
   * Load all local DeviceKeyRecords.
   *
   * @return All stored DeviceKeyRecords.
   */
  public List<DeviceKeyRecord> loadDeviceKeys();

  /**
   * Store a local DeviceKeyRecord.
   *
   * @param deviceKeyId the ID of the DeviceKeyRecord to store.
   * @param record the DeviceKeyRecord.
   */
  public void         storeDeviceKey(int deviceKeyId, DeviceKeyRecord record);

  /**
   * @param deviceKeyId A DeviceKeyRecord ID.
   * @return true if the store has a record for the deviceKeyId, otherwise false.
   */
  public boolean      containsDeviceKey(int deviceKeyId);

  /**
   * Delete a DeviceKeyRecord from local storage.
   *
   * @param deviceKeyId The ID of the PreKeyRecord to remove.
   */
  public void         removeDeviceKey(int deviceKeyId);

}
