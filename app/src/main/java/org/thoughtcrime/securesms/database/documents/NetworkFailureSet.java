package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

public class NetworkFailureSet implements Document<NetworkFailure> {

  @JsonProperty(value = "l")
  private Set<NetworkFailure> failures;

  public NetworkFailureSet() {
    this.failures = new HashSet<>();
  }

  public NetworkFailureSet(Set<NetworkFailure> failures) {
    this.failures = failures;
  }

  @Override
  public int size() {
    if (failures == null) return 0;
    else                  return failures.size();
  }

  @Override
  @JsonIgnore
  public Set<NetworkFailure> getItems() {
    return failures;
  }
}
