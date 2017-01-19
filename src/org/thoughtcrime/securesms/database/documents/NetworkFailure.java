package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NetworkFailure {

  @JsonProperty(value = "r")
  private long recipientId;

  public NetworkFailure(long recipientId) {
    this.recipientId = recipientId;
  }

  public NetworkFailure() {}

  public long getRecipientId() {
    return recipientId;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof NetworkFailure)) return false;

    NetworkFailure that = (NetworkFailure)other;
    return this.recipientId == that.recipientId;
  }

  @Override
  public int hashCode() {
    return (int)recipientId;
  }
}
