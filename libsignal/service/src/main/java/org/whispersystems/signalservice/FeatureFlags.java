package org.whispersystems.signalservice;

/**
 * A location for constants that allows us to turn features on and off at the service level during development.
 * After a feature has been launched, the flag should be removed.
 */
public final class FeatureFlags {

  /** Read and write versioned profile information. */
  public static final boolean VERSIONED_PROFILES = false;
}
