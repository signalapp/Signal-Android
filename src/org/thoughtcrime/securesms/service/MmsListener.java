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
package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.jobs.MmsReceiveJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

public class MmsListener extends BroadcastReceiver {

  private static final String TAG = MmsListener.class.getSimpleName();

  private boolean isRelevant(Context context, Intent intent) {
    if (!ApplicationMigrationService.isDatabaseImported(context)) {
      return false;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
        Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(intent.getAction()) &&
        Util.isDefaultSmsProvider(context))
    {
      return false;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT &&
        TextSecurePreferences.isInterceptAllMmsEnabled(context))
    {
      return true;
    }

    return false;
  }

  @Override
    public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "Got MMS broadcast..." + intent.getAction());

    if ((Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())  &&
        Util.isDefaultSmsProvider(context))                                        ||
        (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(intent.getAction()) &&
         isRelevant(context, intent)))
    {
      Log.i(TAG, "Relevant!");
      int subscriptionId = intent.getExtras().getInt("subscription", -1);

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MmsReceiveJob(context, intent.getByteArrayExtra("data"), subscriptionId));

      abortBroadcast();
    }
  }



}
