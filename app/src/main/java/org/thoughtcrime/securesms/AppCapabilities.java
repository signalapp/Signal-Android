package org.thoughtcrime.securesms;

import org.whispersystems.signalservice.api.account.AccountAttributes;

public final class AppCapabilities {

  private AppCapabilities() {
  }

  private static final boolean UUID_CAPABLE = false;
  private static final boolean GV2_CAPABLE  = true;

  /**
   * @param storageCapable Whether or not the user can use storage service. This is another way of
   *                       asking if the user has set a Signal PIN or not.
   */
  public static AccountAttributes.Capabilities getCapabilities(boolean storageCapable) {
    return new AccountAttributes.Capabilities(UUID_CAPABLE, GV2_CAPABLE, storageCapable);
  }
}
