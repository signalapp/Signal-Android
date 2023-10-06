package org.whispersystems.signalservice.api.groupsv2;


import java.util.Optional;

/**
 * Thrown when a group link:
 * - has an out of date password, or;
 * - is currently not shared, or;
 * - has been banned from the group, or;
 * - the master key does not match a group on the server
 */
public final class GroupLinkNotActiveException extends Exception {

  private final Reason reason;

  public GroupLinkNotActiveException(Throwable t, Optional<String> reason) {
    super(t);

    if (reason.isPresent() && reason.get().equalsIgnoreCase("banned")) {
      this.reason = Reason.BANNED;
    } else {
      this.reason = Reason.UNKNOWN;
    }
  }

  public Reason getReason() {
    return reason;
  }

  public enum Reason {
    UNKNOWN,
    BANNED
  }
}
