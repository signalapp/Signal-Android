package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.state.SignalProtocolStore;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface SignalServiceProtocolStore extends SignalProtocolStore, SignalServiceSessionStore {
}
