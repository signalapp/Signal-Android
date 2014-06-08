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
package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;

public class QuickResponseService extends Service {

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())) {
      Log.w("QuickResponseService", "Received unknown intent: " + intent.getAction());
      return START_NOT_STICKY;
    }

    Toast.makeText(this,
                   getString(R.string.QuickResponseService_sorry_quick_response_is_not_yet_supported_by_textsecure),
                   Toast.LENGTH_LONG).show();

    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
