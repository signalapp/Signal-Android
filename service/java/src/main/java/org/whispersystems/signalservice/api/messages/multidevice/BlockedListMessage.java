package org.whispersystems.signalservice.api.messages.multidevice;

import java.util.List;

public class BlockedListMessage {

  private final List<String> numbers;
  private final List<byte[]> groupIds;

  public BlockedListMessage(List<String> numbers, List<byte[]> groupIds) {
    this.numbers  = numbers;
    this.groupIds = groupIds;
  }

  public List<String> getNumbers() {
    return numbers;
  }

  public List<byte[]> getGroupIds() {
    return groupIds;
  }
}
