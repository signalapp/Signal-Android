package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class IdentityKeyMismatchList {

  @JsonProperty(value = "m")
  private List<IdentityKeyMismatch> mismatches;

  public IdentityKeyMismatchList() {}

  public IdentityKeyMismatchList(List<IdentityKeyMismatch> mismatches) {
    this.mismatches = mismatches;
  }

  public List<IdentityKeyMismatch> getMismatches() {
    return mismatches;
  }


}
