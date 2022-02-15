package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionStore;

import java.util.List;
import java.util.Set;

/**
 * And extension of the normal protocol session store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
public interface SignalServiceSessionStore extends SessionStore {
  void archiveSession(SignalProtocolAddress address);
  Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> addressNames);
}
