package org.whispersystems.signalservice.internal.push;


import com.fasterxml.jackson.annotation.JsonProperty;

public class ProfileAvatarUploadAttributes {

  @JsonProperty
  private String key;

  @JsonProperty
  private String credential;

  @JsonProperty
  private String acl;

  @JsonProperty
  private String algorithm;

  @JsonProperty
  private String date;

  @JsonProperty
  private String policy;

  @JsonProperty
  private String signature;

  public ProfileAvatarUploadAttributes() {}

  public String getKey() {
    return key;
  }

  public String getCredential() {
    return credential;
  }

  public String getAcl() {
    return acl;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public String getDate() {
    return date;
  }

  public String getPolicy() {
    return policy;
  }

  public String getSignature() {
    return signature;
  }
}
