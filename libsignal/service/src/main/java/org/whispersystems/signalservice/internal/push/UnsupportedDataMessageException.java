package org.whispersystems.signalservice.internal.push;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

/**
 * Exception that indicates that the data message has a higher required protocol version than the
 * current client is capable of interpreting.
 */
public class UnsupportedDataMessageException extends Exception {

  private final int                          requiredVersion;
  private final String                       sender;
  private final int                          senderDevice;
  private final Optional<SignalServiceGroup> group;

  public UnsupportedDataMessageException(int currentVersion,
                                         int requiredVersion,
                                         String sender,
                                         int senderDevice,
                                         Optional<SignalServiceGroup> group)
  {
    super("Required version: " + requiredVersion + ", Our version: " + currentVersion);
    this.requiredVersion = requiredVersion;
    this.sender          = sender;
    this.senderDevice    = senderDevice;
    this.group           = group;
  }

  public int getRequiredVersion() {
    return requiredVersion;
  }

  public String getSender() {
    return sender;
  }

  public int getSenderDevice() {
    return senderDevice;
  }

  public Optional<SignalServiceGroup> getGroup() {
    return group;
  }
}
