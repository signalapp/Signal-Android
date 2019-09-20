package org.thoughtcrime.securesms.util;

/**
 * A location for constants that allows us to turn features on and off during development.
 * After a feature has been launched, the flag should be removed.
 */
public class FeatureFlags {
  /** Send support for view-once photos. */
  public static final boolean VIEW_ONCE_SENDING = false;
}
