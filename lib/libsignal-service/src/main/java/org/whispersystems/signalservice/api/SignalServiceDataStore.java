package org.whispersystems.signalservice.api;

import org.signal.core.models.ServiceId;

import javax.annotation.Nullable;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface SignalServiceDataStore {

  /**
   * @return A {@link SignalServiceAccountDataStore} for the specified account.
   */
  SignalServiceAccountDataStore get(ServiceId accountIdentifier);

  /**
   * @return A {@link SignalServiceAccountDataStore} for the ACI account.
   */
  SignalServiceAccountDataStore aci();

  /**
   * @return A {@link SignalServiceAccountDataStore} for the PNI account. Throws if the account has no PNI, so only use this on paths that require a phone
   *         number. Otherwise, use {@link #pniOrNull()}.
   */
  SignalServiceAccountDataStore pni();

  /**
   * @return A {@link SignalServiceAccountDataStore} for the PNI account, or null if the account has no PNI.
   */
  @Nullable SignalServiceAccountDataStore pniOrNull();

  /**
   * @return True if the user has linked devices, otherwise false.
   */
  boolean isMultiDevice();
}
