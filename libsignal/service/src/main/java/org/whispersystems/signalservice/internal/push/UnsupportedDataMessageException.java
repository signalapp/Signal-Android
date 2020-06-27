package org.whispersystems.signalservice.internal.push;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;

/**
 * Exception that indicates that the data message contains something that is not supported by this
 * version of the application. Subclasses provide more specific information about what data was
 * found that is not supported.
 */
public abstract class UnsupportedDataMessageException extends Exception {

  private final String                              sender;
  private final int                                 senderDevice;
  private final Optional<SignalServiceGroupContext> group;

  protected UnsupportedDataMessageException(String message,
                                            String sender,
                                            int senderDevice,
                                            Optional<SignalServiceGroupContext> group)
  {
    super(message);
    this.sender          = sender;
    this.senderDevice    = senderDevice;
    this.group           = group;
  }

  public String getSender() {
    return sender;
  }

  public int getSenderDevice() {
    return senderDevice;
  }

  public Optional<SignalServiceGroupContext> getGroup() {
    return group;
  }
}
