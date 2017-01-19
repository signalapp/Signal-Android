/*
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

package org.thoughtcrime.redphone.signaling;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


/**
 * The authentication mechanism uses HOTP, which requires
 * the client to keep track of a monotonically increasing counter.
 * Using this provider guarantees that the counter is incremented once
 * for each use.
 *
 * @author Moxie Marlinspike
 *
 */
public class OtpCounterProvider {

  private static final OtpCounterProvider provider = new OtpCounterProvider();

  public static OtpCounterProvider getInstance() {
    return provider;
  }

  public synchronized long getOtpCounter(Context context) {
    return 1;
//    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
//    long counter                  = preferences.getLong(Constants.PASSWORD_COUNTER_PREFERENCE, 1L);
//
//    preferences.edit().putLong(Constants.PASSWORD_COUNTER_PREFERENCE, counter+1).commit();
//
//    return counter;
  }

}
