package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class BlockedListMessage {

  private final List<SignalServiceAddress> addresses;
  private final List<byte[]>               groupIds;

  public BlockedListMessage(List<SignalServiceAddress> addresses, List<byte[]> groupIds) {
    this.addresses = addresses;
    this.groupIds  = groupIds;
  }

  public List<SignalServiceAddress> getAddresses() {
    return addresses;
  }

  public List<byte[]> getGroupIds() {
    return groupIds;
  }
}
