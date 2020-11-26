package org.session.libsignal.service.api.crypto;

public class InvalidCiphertextException extends Exception {
  public InvalidCiphertextException(Exception nested) {
    super(nested);
  }

  public InvalidCiphertextException(String s) {
    super(s);
  }
}
