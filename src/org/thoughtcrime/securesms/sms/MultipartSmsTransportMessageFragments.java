package org.thoughtcrime.securesms.sms;

import java.util.Locale;

public class MultipartSmsTransportMessageFragments {

  private static final long VALID_TIME = 60 * 60 * 1000; // 1 Hour

  private final byte[][] fragments;
  private final long initializedTime;

  public MultipartSmsTransportMessageFragments(int count) {
    this.fragments       = new byte[count][];
    this.initializedTime = System.currentTimeMillis();
  }

  public void add(MultipartSmsTransportMessage fragment) {
    this.fragments[fragment.getMultipartIndex()] = fragment.getStrippedMessage();
  }

  public int getSize() {
    return this.fragments.length;
  }

  public boolean isExpired() {
    return (System.currentTimeMillis() - initializedTime) >= VALID_TIME;
  }

  public boolean isComplete() {
    for (int i=0;i<fragments.length;i++)
      if (fragments[i] == null) return false;

    return true;
  }

  public byte[] getJoined() {
    int totalMessageLength = 0;

    for (int i=0;i<fragments.length;i++) {
      totalMessageLength += fragments[i].length;
    }

    byte[] totalMessage    = new byte[totalMessageLength];
    int totalMessageOffset = 0;

    for (int i=0;i<fragments.length;i++) {
      System.arraycopy(fragments[i], 0, totalMessage, totalMessageOffset, fragments[i].length);
      totalMessageOffset += fragments[i].length;
    }

    return totalMessage;
  }

  @Override
  public String toString() {
    return String.format(Locale.getDefault(),"[Size: %d, Initialized: %d, Exipired: %s, Complete: %s]",
                         fragments.length, initializedTime, isExpired()+"", isComplete()+"");
  }
}
