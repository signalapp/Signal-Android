package org.session.libsignal.metadata;


public class InvalidMetadataMessageException extends Exception {
  public InvalidMetadataMessageException(String s) {
    super(s);
  }

  public InvalidMetadataMessageException(Exception s) {
    super(s);
  }

}
