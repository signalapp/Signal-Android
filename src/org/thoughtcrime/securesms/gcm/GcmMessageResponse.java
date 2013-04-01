package org.thoughtcrime.securesms.gcm;

import java.util.List;

public class GcmMessageResponse {
  private List<String> success;
  private List<String> failure;

  public List<String> getSuccess() {
    return success;
  }

  public List<String> getFailure() {
    return failure;
  }


}
