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

  /** Favoring profile names when displaying contacts. */
  public static final boolean PROFILE_DISPLAY = UUIDS;

  /** MessageRequest stuff */
  public static final boolean MESSAGE_REQUESTS = UUIDS;

  /** Creating usernames, sending messages by username. Requires {@link #UUIDS}. */
  public static final boolean USERNAMES = false;

  /** Set or migrate PIN to KBS */
  public static final boolean KBS = false;

  /** Storage service. Requires {@link #KBS}. */
  public static final boolean STORAGE_SERVICE = false;

  /** Send support for reactions. */
  public static final boolean REACTION_SENDING = false;
}
