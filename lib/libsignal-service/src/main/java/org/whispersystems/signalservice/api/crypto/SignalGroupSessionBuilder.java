package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.GroupSessionBuilder;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
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
