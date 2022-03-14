package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

public class IdentityKeyMismatchSet implements Document<IdentityKeyMismatch> {

  @JsonProperty(value = "m")
  private Set<IdentityKeyMismatch> mismatches;

  public IdentityKeyMismatchSet() {
    this.mismatches = new HashSet<>();
  }

  public IdentityKeyMismatchSet(Set<IdentityKeyMismatch> mismatches) {
    this.mismatches = mismatches;
  }

  @Override
  public int size() {
    if (mismatches == null) return 0;
    else                    return mismatches.size();
  }

  @Override
  @JsonIgnore
  public Set<IdentityKeyMismatch> getItems() {
    return mismatches;
  }
}
