/*
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
import android.text.SpannableString;
import androidx.annotation.NonNull;
import org.session.libsession.utilities.IdentityKeyMismatch;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.database.SmsDatabase;
import java.util.LinkedList;
import java.util.List;
import network.loki.messenger.R;

/**
 * The message record model which represents standard SMS messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class SmsMessageRecord extends MessageRecord {

  public SmsMessageRecord(long id,
    String body, Recipient recipient,
    Recipient individualRecipient,
    long dateSent, long dateReceived,
    int deliveryReceiptCount,
    long type, long threadId,
    int status, List<IdentityKeyMismatch> mismatches,
    long expiresIn, long expireStarted,
    int readReceiptCount, boolean unidentified)
  {
    super(id, body, recipient, individualRecipient,
      dateSent, dateReceived, threadId, status, deliveryReceiptCount, type,
      mismatches, new LinkedList<>(),
      expiresIn, expireStarted, readReceiptCount, unidentified);
  }

  public long getType() {
    return type;
  }

  @Override
  public SpannableString getDisplayBody(@NonNull Context context) {
    if (SmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
    } else if (SmsDatabase.Types.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (SmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
    } else {
      return super.getDisplayBody(context);
    }
  }

  @Override
  public boolean isMms() {
    return false;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }
}
