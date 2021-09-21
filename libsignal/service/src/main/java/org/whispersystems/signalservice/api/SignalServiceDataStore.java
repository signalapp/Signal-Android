package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.state.SignalProtocolStore;

import java.io.Closeable;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface SignalServiceDataStore extends SignalProtocolStore, SignalServiceSessionStore, SignalServiceSenderKeyStore {
  /**
   * @return True if the active account has linked devices, otherwise false.
   */
  boolean isMultiDevice();

  /**
   * @return Begins a transaction to improve the performance of multiple storage operations happening in a row.
   */
  Transaction beginTransaction();

  interface Transaction extends Closeable {
    void close();
  }
}
