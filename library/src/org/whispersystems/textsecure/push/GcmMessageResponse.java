package org.whispersystems.textsecure.push;

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
