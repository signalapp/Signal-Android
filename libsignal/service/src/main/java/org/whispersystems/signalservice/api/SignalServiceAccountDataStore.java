package org.whispersystems.signalservice.api;

import org.signal.libsignal.protocol.state.SignalProtocolStore;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface SignalServiceAccountDataStore extends SignalProtocolStore,
                                                       SignalServicePreKeyStore,
                                                       SignalServiceSessionStore,
                                                       SignalServiceSenderKeyStore,
                                                       SignalServiceKyberPreKeyStore {
  /**
   * @return True if the user has linked devices, otherwise false.
   */
  boolean isMultiDevice();
}
