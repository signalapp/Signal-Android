package org.whispersystems.signalservice.api.groupsv2;

/**
 * Thrown when a group link:
 * - has an out of date password, or;
 * - is currently not shared, or;
 * - the master key does not match a group on the server
 */
public final class GroupLinkNotActiveException extends Exception {
  public GroupLinkNotActiveException() {
  }

  public GroupLinkNotActiveException(Throwable t) {
    super(t);
  }
}
