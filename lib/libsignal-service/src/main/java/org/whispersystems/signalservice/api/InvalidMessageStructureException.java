package org.whispersystems.signalservice.api;


import java.util.Optional;

/**
 * An exception thrown when something about the proto is malformed. e.g. one of the fields has an invalid value.
 */
public final class InvalidMessageStructureException extends Exception {

  private final Optional<String>  sender;
  private final Optional<Integer> device;

  public InvalidMessageStructureException(String message) {
    super(message);
    this.sender = Optional.empty();
    this.device = Optional.empty();
  }

  public InvalidMessageStructureException(String message, String sender, int device) {
    super(message);
    this.sender = Optional.ofNullable(sender);
    this.device = Optional.of(device);
  }

  public InvalidMessageStructureException(Exception e, String sender, int device) {
    super(e);
    this.sender = Optional.ofNullable(sender);
    this.device = Optional.of(device);
  }

  public InvalidMessageStructureException(Exception e) {
    super(e);
    this.sender = Optional.empty();
    this.device = Optional.empty();
  }

  public Optional<String> getSender() {
    return sender;
  }

  public Optional<Integer> getDevice() {
    return device;
  }
}
