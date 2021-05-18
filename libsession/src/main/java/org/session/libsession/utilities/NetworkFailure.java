package org.session.libsession.utilities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.session.libsession.utilities.Address;

public class NetworkFailure {

  @JsonProperty(value = "a")
  private String address;

  public NetworkFailure(Address address) {
    this.address = address.serialize();
  }

  public NetworkFailure() {}

  @JsonIgnore
  public Address getAddress() {
    return Address.fromSerialized(address);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof NetworkFailure)) return false;

    NetworkFailure that = (NetworkFailure)other;
    return this.address.equals(that.address);
  }

  @Override
  public int hashCode() {
    return address.hashCode();
  }
}
