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
import android.text.SpannableString;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.textsecure.util.ListenableFutureTask;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

public class MediaMmsMessageRecord extends MessageRecord {

  private final Context context;
  private final int partCount;
  private final ListenableFutureTask<SlideDeck> slideDeck;

  public MediaMmsMessageRecord(Context context, long id, Recipients recipients,
                               Recipient individualRecipient, int recipientDeviceId,
                               long dateSent, long dateReceived, long threadId, Body body,
                               ListenableFutureTask<SlideDeck> slideDeck,
                               int partCount, long mailbox)
  {
    super(context, id, body, recipients, individualRecipient, recipientDeviceId,
          dateSent, dateReceived, threadId, DELIVERY_STATUS_NONE, mailbox);

    this.context   = context.getApplicationContext();
    this.partCount = partCount;
    this.slideDeck = slideDeck;
  }

  public ListenableFutureTask<SlideDeck> getSlideDeck() {
    return slideDeck;
  }

  public int getPartCount() {
    return partCount;
  }

  @Override
  public boolean isMms() {
    return true;
  }

  @Override
  public SpannableString getDisplayBody() {
    if (MmsDatabase.Types.isDecryptInProgressType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_decrypting_mms_please_wait));
    } else if (MmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
    } else if (MmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
    } else if (!getBody().isPlaintext()) {
      return emphasisAdded(context.getString(R.string.MessageNotifier_encrypted_message));
    }

    return super.getDisplayBody();
  }
}
