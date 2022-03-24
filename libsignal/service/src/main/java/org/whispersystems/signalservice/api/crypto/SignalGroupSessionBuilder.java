package org.whispersystems.signalservice.api.crypto;

import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.GroupSessionBuilder;
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage;
import org.whispersystems.signalservice.api.SignalSessionLock;

import java.util.UUID;

/**
 * A thread-safe wrapper around {@link SessionBuilder}.
 */
public class SignalGroupSessionBuilder {

  private final SignalSessionLock   lock;
  private final GroupSessionBuilder builder;

  public SignalGroupSessionBuilder(SignalSessionLock lock, GroupSessionBuilder builder) {
    this.lock    = lock;
    this.builder = builder;
  }

  public void process(SignalProtocolAddress sender, SenderKeyDistributionMessage senderKeyDistributionMessage) {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      builder.process(sender, senderKeyDistributionMessage);
    }
  }

  public SenderKeyDistributionMessage create(SignalProtocolAddress sender, UUID distributionId) {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return builder.create(sender, distributionId);
    }
  }
}
