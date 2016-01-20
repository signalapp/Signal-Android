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
package org.privatechats.securesms.database.model;

import android.content.Context;
import android.text.SpannableString;

import org.privatechats.securesms.R;
import org.privatechats.securesms.database.SmsDatabase.Status;
import org.privatechats.securesms.database.MmsDatabase;
import org.privatechats.securesms.database.documents.NetworkFailure;
import org.privatechats.securesms.database.documents.IdentityKeyMismatch;
import org.privatechats.securesms.recipients.Recipient;
import org.privatechats.securesms.recipients.Recipients;

import java.util.LinkedList;

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

  public NotificationMmsMessageRecord(Context context, long id, Recipients recipients,
                                      Recipient individualRecipient, int recipientDeviceId,
                                      long dateSent, long dateReceived, int receiptCount,
                                      long threadId, byte[] contentLocation, long messageSize,
                                      long expiry, int status, byte[] transactionId, long mailbox)
  {
    super(context, id, new Body("", true), recipients, individualRecipient, recipientDeviceId,
          dateSent, dateReceived, threadId, Status.STATUS_NONE, receiptCount, mailbox,
          new LinkedList<IdentityKeyMismatch>(), new LinkedList<NetworkFailure>());

    this.contentLocation = contentLocation;
    this.messageSize     = messageSize;
    this.expiry          = expiry;
    this.status          = status;
    this.transactionId   = transactionId;
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
    return MmsDatabase.Status.isHardError(status);
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

  @Override
  public boolean isMmsNotification() {
    return true;
  }

  @Override
  public SpannableString getDisplayBody() {
    return emphasisAdded(context.getString(R.string.NotificationMmsMessageRecord_multimedia_message));
  }
}
