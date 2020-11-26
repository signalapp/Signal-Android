/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.libsignal.groups.state;

import org.session.libsignal.libsignal.groups.SenderKeyName;
import org.session.libsignal.libsignal.groups.state.SenderKeyRecord;

public interface SenderKeyStore {

  /**
   * Commit to storage the {@link org.session.libsignal.libsignal.groups.state.SenderKeyRecord} for a
   * given (groupId + senderId + deviceId) tuple.
   *
   * @param senderKeyName the (groupId + senderId + deviceId) tuple.
   * @param record the current SenderKeyRecord for the specified senderKeyName.
   */
  public void storeSenderKey(SenderKeyName senderKeyName, SenderKeyRecord record);

  /**
   * Returns a copy of the {@link SenderKeyRecord}
   * corresponding to the (groupId + senderId + deviceId) tuple, or a new SenderKeyRecord if
   * one does not currently exist.
   * <p>
   * It is important that implementations return a copy of the current durable information.  The
   * returned SenderKeyRecord may be modified, but those changes should not have an effect on the
   * durable session state (what is returned by subsequent calls to this method) without the
   * store method being called here first.
   *
   * @param senderKeyName The (groupId + senderId + deviceId) tuple.
   * @return a copy of the SenderKeyRecord corresponding to the (groupId + senderId + deviceId tuple, or
   *         a new SenderKeyRecord if one does not currently exist.
   */

  public SenderKeyRecord loadSenderKey(SenderKeyName senderKeyName);
}
