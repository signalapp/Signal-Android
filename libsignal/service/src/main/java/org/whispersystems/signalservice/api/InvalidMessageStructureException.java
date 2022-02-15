package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.util.guava.Optional;

/**
 * An exception thrown when something about the proto is malformed. e.g. one of the fields has an invalid value.
 */
public final class InvalidMessageStructureException extends Exception {

  private final Optional<String>  sender;
  private final Optional<Integer> device;

  public InvalidMessageStructureException(String message) {
    super(message);
    this.sender = Optional.absent();
    this.device = Optional.absent();
  }

  public InvalidMessageStructureException(String message, String sender, int device) {
    super(message);
    this.sender = Optional.fromNullable(sender);
    this.device = Optional.of(device);
  }

  public InvalidMessageStructureException(Exception e, String sender, int device) {
    super(e);
    this.sender = Optional.fromNullable(sender);
    this.device = Optional.of(device);
  }

  public InvalidMessageStructureException(Exception e) {
    super(e);
    this.sender = Optional.absent();
    this.device = Optional.absent();
  }

  public Optional<String> getSender() {
    return sender;
  }

  public Optional<Integer> getDevice() {
    return device;
  }
}
