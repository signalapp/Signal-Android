package org.whispersystems.textsecure.api.util;

public interface CredentialsProvider {
  public String getUser();
  public String getPassword();
  public String getSignalingKey();
}
