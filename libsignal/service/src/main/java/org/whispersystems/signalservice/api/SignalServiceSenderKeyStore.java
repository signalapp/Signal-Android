package org.whispersystems.signalservice.api;

import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyStore;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.Set;

/**
 * And extension of the normal protocol sender key store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
public interface SignalServiceSenderKeyStore extends SenderKeyStore {
  /**
   * @return A set of protocol addresses that have previously been sent the sender key data for the provided distributionId.
   */
  Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId);

  /**
   * Marks the provided addresses as having been sent the sender key data for the provided distributionId.
   */
  void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses);

  /**
   * Marks the provided addresses as not knowing about any distributionIds.
   */
  void clearSenderKeySharedWith(Collection<SignalProtocolAddress> addresses);
}
