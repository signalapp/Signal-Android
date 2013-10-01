package org.whispersystems.textsecure.push;

import java.io.IOException;

public class NotFoundException extends IOException {
  public NotFoundException(String s) {
    super(s);
  }
}
