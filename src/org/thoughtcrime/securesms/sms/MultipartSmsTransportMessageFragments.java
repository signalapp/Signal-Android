package org.thoughtcrime.securesms.sms;

public class MultipartSmsTransportMessageFragments {

  private final byte[][] fragments;

  public MultipartSmsTransportMessageFragments(int count) {
    this.fragments = new byte[count][];
  }

  public void add(MultipartSmsTransportMessage fragment) {
    this.fragments[fragment.getMultipartIndex()] = fragment.getStrippedMessage();
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

}
