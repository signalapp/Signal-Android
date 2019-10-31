package org.thoughtcrime.securesms.util;

/**
 * A location for constants that allows us to turn features on and off during development.
 * After a feature has been launched, the flag should be removed.
 */
public class FeatureFlags {
  /** Send support for view-once media. */
  public static final boolean VIEW_ONCE_SENDING = false;

  /** UUID-related stuff that shouldn't be activated until the user-facing launch. */
  public static final boolean UUIDS = false;

  /** Usernames. */
  public static final boolean USERNAMES = false;

  /** New Profile Display */
  public static final boolean PROFILE_DISPLAY = UUIDS;
}
