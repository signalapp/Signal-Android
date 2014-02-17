package org.whispersystems.textsecure.push;

public class AccountAttributes {

  private String signalingKey;
  private boolean supportsSms;

  public AccountAttributes(String signalingKey, boolean supportsSms) {
    this.signalingKey = signalingKey;
    this.supportsSms  = supportsSms;
  }

  public AccountAttributes() {}

  public String getSignalingKey() {
    return signalingKey;
  }

  public boolean isSupportsSms() {
    return supportsSms;
  }
}
