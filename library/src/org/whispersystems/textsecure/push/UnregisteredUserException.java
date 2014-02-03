package org.whispersystems.textsecure.push;

import java.io.IOException;
import java.util.List;

public class UnregisteredUserException extends IOException {

  public UnregisteredUserException(Exception exception) {
    super(exception);
  }

}
