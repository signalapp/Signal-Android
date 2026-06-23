/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer;

import androidx.annotation.NonNull;

/**
 * Progress event posted on the new device as the incoming backup is received and imported.
 * Posted by the concrete {@code NewDeviceServerTask} in the app module and observed by whichever
 * UI layer is driving the transfer (app or feature/registration).
 */
public final class NewDeviceRestoreStatus {

  private final long messageCount;
  private final State state;

  public NewDeviceRestoreStatus(long messageCount, @NonNull State state) {
    this.messageCount = messageCount;
    this.state = state;
  }

  public long getMessageCount() {
    return messageCount;
  }

  public @NonNull State getState() {
    return state;
  }

  public enum State {
    IN_PROGRESS,
    TRANSFER_COMPLETE,
    RESTORE_COMPLETE,
    FAILURE_VERSION_DOWNGRADE,
    FAILURE_FOREIGN_KEY,
    FAILURE_UNKNOWN
  }
}
