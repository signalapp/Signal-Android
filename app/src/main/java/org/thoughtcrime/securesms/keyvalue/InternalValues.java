package org.thoughtcrime.securesms.keyvalue;

import org.thoughtcrime.securesms.util.FeatureFlags;

public final class InternalValues extends SignalStoreValues {

  public static final String GV2_DO_NOT_CREATE_GV2     = "internal.gv2.do_not_create_gv2";
  public static final String GV2_FORCE_INVITES         = "internal.gv2.force_invites";
  public static final String GV2_IGNORE_SERVER_CHANGES = "internal.gv2.ignore_server_changes";
  public static final String GV2_IGNORE_P2P_CHANGES    = "internal.gv2.ignore_p2p_changes";

  InternalValues(KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  /**
   * Do not attempt to create GV2 groups, i.e. will force creation of GV1 or MMS groups.
   */
  public synchronized boolean gv2DoNotCreateGv2Groups() {
    return FeatureFlags.internalUser() && getBoolean(GV2_DO_NOT_CREATE_GV2, false);
  }

  /**
   * Members will not be added directly to a GV2 even if they could be.
   */
  public synchronized boolean gv2ForceInvites() {
    return FeatureFlags.internalUser() && getBoolean(GV2_FORCE_INVITES, false);
  }

  /**
   * The Server will leave out changes that can only be described by a future protocol level that
   * an older client cannot understand. Ignoring those changes by nulling them out simulates that
   * scenario for testing.
   * <p>
   * In conjunction with {@link #gv2IgnoreP2PChanges()} it means no group changes are coming into
   * the client and it will generate changes by group state comparison, and those changes will not
   * have an editor and so will be in the passive voice.
   */
  public synchronized boolean gv2IgnoreServerChanges() {
    return FeatureFlags.internalUser() && getBoolean(GV2_IGNORE_SERVER_CHANGES, false);
  }

  /**
   * Signed group changes are sent P2P, if the client ignores them, it will then ask the server
   * directly which allows testing of certain testing scenarios.
   */
  public synchronized boolean gv2IgnoreP2PChanges() {
    return FeatureFlags.internalUser() && getBoolean(GV2_IGNORE_P2P_CHANGES, false);
  }
}
