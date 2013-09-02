/**
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.thoughtcrime.securesms.database.keys.LocalKeyRecord;
import org.thoughtcrime.securesms.database.keys.RemoteKeyRecord;
import org.thoughtcrime.securesms.database.keys.SessionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;

/**
 * This class processes abort session interactions.
 *
 * @author Moxie Marlinspike
 */

public class AbortSessionProcessor {
  public static final String ABORT_SESSION_EVENT = "org.thoughtcrime.securesms.KEY_EXCHANGE_ABORT_SESSION";

  private Context context;
  private Recipient recipient;

  public AbortSessionProcessor(Context context, Recipient recipient) {
    this.context         = context;
    this.recipient       = recipient;
  }

  public void processAbortSessionMessage(AbortSessionMessage abortSessionMessage, long threadId) {
    RemoteKeyRecord remoteKeyRecord = new RemoteKeyRecord(context, recipient);

    if (!RemoteKeyRecord.hasRecord(context, recipient)) {
        Log.w("AbortSessionProcessor", "Unknown abort message. Dropping message...");
        return;
    }

    PublicKey storedPublicKey = remoteKeyRecord.getCurrentRemoteKey();
    if (storedPublicKey.equals(abortSessionMessage.getPublicKey())) {
        Log.w("AbortSessionProcessor", "deleting local keys");
        LocalKeyRecord.delete(context, recipient);
        RemoteKeyRecord.delete(context, recipient);
        SessionRecord.delete(context, recipient);

        Intent intent = new Intent(ABORT_SESSION_EVENT);
        intent.putExtra("thread_id", threadId);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);
    } else {
        Log.w("AbortSessionProcessor", "Failed to abort session due to invalid public key");
    }
  }
}
