/**
 * Copyright (C) 2015 Open Whisper Systems
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
package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;

public abstract class MessageRecipientAsyncTask extends AsyncTask<MessageRecord,Void,Recipients> {
  private static final String TAG = MessageRecipientAsyncTask.class.getSimpleName();

  private WeakReference<Context> weakContext;

  public MessageRecipientAsyncTask(Context context) {
    this.weakContext  = new WeakReference<>(context);
  }

  protected Context getContext() {
    return weakContext.get();
  }

  @Override
  public Recipients doInBackground(MessageRecord... messageRecords) {
    if (messageRecords.length != 1) throw new AssertionError("one message record at at time");
    MessageRecord messageRecord = messageRecords[0];
    Context context = getContext();
    if (context == null) {
      Log.w(TAG, "associated context is destroyed, finishing early");
    }

    Recipients    recipients;

    final Recipients intermediaryRecipients;
    if (messageRecord.isMms()) {
      intermediaryRecipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageRecord.getId());
    } else {
      intermediaryRecipients = messageRecord.getRecipients();
    }

    if (!intermediaryRecipients.isGroupRecipient()) {
      Log.w(TAG, "Recipient is not a group, resolving members immediately.");
      recipients = intermediaryRecipients;
    } else {
      try {
        String groupId = intermediaryRecipients.getPrimaryRecipient().getNumber();
        recipients = DatabaseFactory.getGroupDatabase(context)
                                    .getGroupMembers(GroupUtil.getDecodedId(groupId), false);
      } catch (IOException e) {
        Log.w(TAG, e);
       recipients = new Recipients(new LinkedList<Recipient>());
      }
    }

    return recipients;
  }
}
