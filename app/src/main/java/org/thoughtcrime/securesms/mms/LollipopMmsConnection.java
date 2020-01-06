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
package org.thoughtcrime.securesms.mms;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;

import java.util.concurrent.TimeoutException;

public abstract class LollipopMmsConnection extends BroadcastReceiver {
  private static final String TAG = LollipopMmsConnection.class.getSimpleName();

  private final Context context;
  private final String action;

  private boolean resultAvailable;

  public abstract void onResult(Context context, Intent intent);

  protected LollipopMmsConnection(Context context, String action) {
    super();
    this.context = context;
    this.action  = action;
  }

  @Override
  public synchronized void onReceive(Context context, Intent intent) {
    Log.i(TAG, "onReceive()");
    if (!action.equals(intent.getAction())) {
      Log.w(TAG, "received broadcast with unexpected action " + intent.getAction());
      return;
    }

    onResult(context, intent);

    resultAvailable = true;
    notifyAll();
  }

  protected void beginTransaction() {
    getContext().getApplicationContext().registerReceiver(this, new IntentFilter(action));
  }

  protected void endTransaction() {
    getContext().getApplicationContext().unregisterReceiver(this);
    resultAvailable = false;
  }

  protected void waitForResult() throws TimeoutException {
    long timeoutExpiration = System.currentTimeMillis() + 60000;
    while (!resultAvailable) {
      Util.wait(this, Math.max(1, timeoutExpiration - System.currentTimeMillis()));
      if (System.currentTimeMillis() >= timeoutExpiration) {
        throw new TimeoutException("timeout when waiting for MMS");
      }
    }
  }

  protected PendingIntent getPendingIntent() {
    return PendingIntent.getBroadcast(getContext(), 1, new Intent(action), PendingIntent.FLAG_ONE_SHOT);
  }

  protected Context getContext() {
    return context;
  }
}
