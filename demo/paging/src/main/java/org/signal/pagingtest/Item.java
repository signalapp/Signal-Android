package org.signal.pagingtest;

public final class Item {
  final String key;
  final long   timestamp;

  public Item(String key, long timestamp) {
    this.key       = key;
    this.timestamp = timestamp;
  }
}
