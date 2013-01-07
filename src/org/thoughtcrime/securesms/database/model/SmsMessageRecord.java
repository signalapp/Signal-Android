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

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * The message record model which represents standard SMS messages.
 *
 * @author Moxie Marlinspike
 *
 */

public class SmsMessageRecord extends MessageRecord {

  private final Context context;
  private final long type;

  public SmsMessageRecord(Context context, long id,
                          Recipients recipients,
                          Recipient individualRecipient,
                          long dateSent, long dateReceived,
                          long type, long threadId,
                          int status, GroupData groupData)
  {
    super(id, recipients, individualRecipient, dateSent, dateReceived,
          threadId, getGenericDeliveryStatus(status), groupData);
    this.context  = context.getApplicationContext();
    this.type     = type;
  }

  public long getType() {
    return type;
  }

  @Override
  public void setBody(String body) {
    if (this.type == SmsDatabase.Types.FAILED_DECRYPT_TYPE) {
      super.setBody(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
      setEmphasis(true);
    } else if (this.type == SmsDatabase.Types.DECRYPT_IN_PROGRESS_TYPE   ||
               (type == 0 && body.startsWith(Prefix.ASYMMETRIC_ENCRYPT)) ||
               (type == 0 && body.startsWith(Prefix.ASYMMETRIC_LOCAL_ENCRYPT)))
    {
      super.setBody(context.getString(R.string.MessageDisplayHelper_decrypting_please_wait));
      setEmphasis(true);
    } else if (type == SmsDatabase.Types.NO_SESSION_TYPE) {
      super.setBody(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
      setEmphasis(true);
    } else {
      super.setBody(body);
    }
  }

  @Override
  public boolean isFailed() {
    return SmsDatabase.Types.isFailedMessageType(getType()) ||
           getDeliveryStatus() == DELIVERY_STATUS_FAILED;
  }

  @Override
  public boolean isOutgoing() {
    return SmsDatabase.Types.isOutgoingMessageType(getType());
  }

  @Override
  public boolean isPending() {
    return SmsDatabase.Types.isPendingMessageType(getType()) || isGroupDeliveryPending();
  }

  @Override
  public boolean isSecure() {
    return SmsDatabase.Types.isSecureType(getType());
  }

  @Override
  public boolean isMms() {
    return false;
  }

  private static int getGenericDeliveryStatus(int status) {
    if (status == SmsDatabase.Status.STATUS_NONE) {
      return MessageRecord.DELIVERY_STATUS_NONE;
    } else if (status >= SmsDatabase.Status.STATUS_FAILED) {
      return MessageRecord.DELIVERY_STATUS_FAILED;
    } else if (status >= SmsDatabase.Status.STATUS_PENDING) {
      return MessageRecord.DELIVERY_STATUS_PENDING;
    } else {
      return MessageRecord.DELIVERY_STATUS_RECEIVED;
    }
  }
}
