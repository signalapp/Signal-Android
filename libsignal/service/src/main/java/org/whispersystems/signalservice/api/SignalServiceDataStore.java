package org.whispersystems.signalservice.api;

import org.whispersystems.signalservice.api.push.AccountIdentifier;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface SignalServiceDataStore {

  /**
   * @return A {@link SignalServiceAccountDataStore} for the specified account.
   */
  SignalServiceAccountDataStore get(AccountIdentifier accountIdentifier);

  /**
   * @return A {@link SignalServiceAccountDataStore} for the ACI account.
   */
  SignalServiceAccountDataStore aci();

  /**
   * @return A {@link SignalServiceAccountDataStore} for the PNI account.
   */
  SignalServiceAccountDataStore pni();

  /**
   * @return True if the user has linked devices, otherwise false.
   */
  boolean isMultiDevice();
}
