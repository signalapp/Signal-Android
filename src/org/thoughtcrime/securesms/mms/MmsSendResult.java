/**
 * Copyright (C) 2013-2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.mms;

public class MmsSendResult {

  private final byte[]  messageId;
  private final int     responseStatus;
  private final boolean upgradedSecure;
  private final boolean push;

  public MmsSendResult(byte[] messageId, int responseStatus, boolean upgradedSecure, boolean push) {
    this.messageId      = messageId;
    this.responseStatus = responseStatus;
    this.upgradedSecure = upgradedSecure;
    this.push           = push;
  }

  public boolean isPush() {
    return push;
  }

  public boolean isUpgradedSecure() {
    return upgradedSecure;
  }

  public int getResponseStatus() {
    return responseStatus;
  }

  public byte[] getMessageId() {
    return messageId;
  }
}
