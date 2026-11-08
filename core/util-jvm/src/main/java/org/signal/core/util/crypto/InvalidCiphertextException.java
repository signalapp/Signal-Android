package org.signal.core.util.crypto;

public class InvalidCiphertextException extends Exception {
  public InvalidCiphertextException(Exception nested) {
    super(nested);
  }

  public InvalidCiphertextException(String s) {
    super(s);
  }
}
