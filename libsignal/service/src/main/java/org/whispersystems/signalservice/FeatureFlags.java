package org.whispersystems.signalservice;

/**
 * A location for constants that allows us to turn features on and off at the service level during development.
 * After a feature has been launched, the flag should be removed.
 */
public final class FeatureFlags {
  /** Zero Knowledge Group functions */
  public static final boolean ZK_GROUPS = false;

  /** Read and write versioned profile information. */
  public static final boolean VERSIONED_PROFILES = ZK_GROUPS && false;
}
