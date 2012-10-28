/**
 * Copyright (C) 2012 Moxie Marlinspike
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
package org.thoughtcrime.securesms.database.model;

import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * Represents the message record model for MMS messages that are
 * notifications (ie: they're pointers to undownloaded media).
 *
 * @author Moxie Marlinspike
 *
 */

public class NotificationMmsMessageRecord extends MessageRecord {

  private final byte[] contentLocation;
  private final long messageSize;
  private final long expiry;
  private final int status;
  private final byte[] transactionId;

  public NotificationMmsMessageRecord(long id, Recipients recipients, Recipient individualRecipient,
                                      long date, long threadId, byte[] contentLocation,
                                      long messageSize, long expiry,
                                      int status, byte[] transactionId)
  {
    super(id, recipients, individualRecipient, date, threadId);
    this.contentLocation = contentLocation;
    this.messageSize     = messageSize;
    this.expiry          = expiry;
    this.status          = status;
    this.transactionId   = transactionId;

    setBody("Multimedia Message");
    setEmphasis(true);
  }

  public byte[] getTransactionId() {
    return transactionId;
  }

  public int getStatus() {
    return this.status;
  }


  public byte[] getContentLocation() {
    return contentLocation;
  }

  public long getMessageSize() {
    return (messageSize + 1023) / 1024;
  }

  public long getExpiration() {
    return expiry * 1000;
  }

  @Override
  public boolean isOutgoing() {
    return false;
  }

  @Override
  public boolean isFailed() {
    return MmsDatabase.Types.isHardError(status);
  }

  @Override
  public boolean isSecure() {
    return false;
  }

  @Override
  public boolean isPending() {
    return false;
  }

  @Override
  public boolean isMms() {
    return true;
  }

}
