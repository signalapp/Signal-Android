package org.whispersystems.textsecure.push;

import java.io.IOException;
import java.util.List;

public class UnregisteredUserException extends IOException {

  private final List<String> addresses;

  public UnregisteredUserException(List<String> addresses) {
    super();
    this.addresses = addresses;
  }

  public List<String> getAddresses() {
    return addresses;
  }

}
