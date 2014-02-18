package org.whispersystems.textsecure.push;

public class AccountAttributes {

  private String  signalingKey;
  private boolean supportsSms;
  private int     registrationId;

  public AccountAttributes(String signalingKey, boolean supportsSms, int registrationId) {
    this.signalingKey   = signalingKey;
    this.supportsSms    = supportsSms;
    this.registrationId = registrationId;
  }

  public AccountAttributes() {}

  public String getSignalingKey() {
    return signalingKey;
  }

  public boolean isSupportsSms() {
    return supportsSms;
  }

  public int getRegistrationId() {
    return registrationId;
  }
}
