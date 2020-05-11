package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class MessageRequestResponseMessage {

  private final Optional<SignalServiceAddress> person;
  private final Optional<byte[]>               groupId;
  private final Type                           type;

  public static MessageRequestResponseMessage forIndividual(SignalServiceAddress address, Type type) {
    return new MessageRequestResponseMessage(Optional.of(address), Optional.<byte[]>absent(), type);
  }

  public static MessageRequestResponseMessage forGroup(byte[] groupId, Type type) {
    return new MessageRequestResponseMessage(Optional.<SignalServiceAddress>absent(), Optional.of(groupId), type);
  }

  private MessageRequestResponseMessage(Optional<SignalServiceAddress> person,
                                        Optional<byte[]> groupId,
                                        Type type)
  {
    this.person  = person;
    this.groupId = groupId;
    this.type    = type;
  }

  public Optional<SignalServiceAddress> getPerson() {
    return person;
  }

  public Optional<byte[]> getGroupId() {
    return groupId;
  }

  public Type getType() {
    return type;
  }

  public enum Type {
    UNKNOWN, ACCEPT, DELETE, BLOCK, BLOCK_AND_DELETE, UNBLOCK_AND_ACCEPT
  }
}
