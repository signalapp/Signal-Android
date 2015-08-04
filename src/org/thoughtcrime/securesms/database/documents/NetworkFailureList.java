package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

public class NetworkFailureList implements Document<NetworkFailure> {

  @JsonProperty(value = "l")
  private List<NetworkFailure> failures;

  public NetworkFailureList() {
    this.failures = new LinkedList<>();
  }

  public NetworkFailureList(List<NetworkFailure> failures) {
    this.failures = failures;
  }

  @Override
  public int size() {
    if (failures == null) return 0;
    else                  return failures.size();
  }

  @Override
  @JsonIgnore
  public List<NetworkFailure> getList() {
    return failures;
  }
}
