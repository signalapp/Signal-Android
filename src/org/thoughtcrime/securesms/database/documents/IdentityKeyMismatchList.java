package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

public class IdentityKeyMismatchList implements Document<IdentityKeyMismatch> {

  @JsonProperty(value = "m")
  private List<IdentityKeyMismatch> mismatches;

  public IdentityKeyMismatchList() {
    this.mismatches = new LinkedList<>();
  }

  public IdentityKeyMismatchList(List<IdentityKeyMismatch> mismatches) {
    this.mismatches = mismatches;
  }

  @Override
  public int size() {
    if (mismatches == null) return 0;
    else                    return mismatches.size();
  }

  @Override
  @JsonIgnore
  public List<IdentityKeyMismatch> getList() {
    return mismatches;
  }
}
