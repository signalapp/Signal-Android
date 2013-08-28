package org.whispersystems.textsecure.push;

public class SignalingKey {

  private String signalingKey;

  public SignalingKey(String signalingKey) {
    this.signalingKey = signalingKey;
  }

  public SignalingKey() {}

  public String getSignalingKey() {
    return signalingKey;
  }
}
