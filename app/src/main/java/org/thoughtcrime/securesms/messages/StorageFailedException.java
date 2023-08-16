/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages;

class StorageFailedException extends Exception {
  private final String sender;
  private final int    senderDevice;

  StorageFailedException(Exception e, String sender, int senderDevice) {
    super(e);
    this.sender       = sender;
    this.senderDevice = senderDevice;
  }

  public String getSender() {
    return sender;
  }

  public int getSenderDevice() {
    return senderDevice;
  }
}
