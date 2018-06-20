/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.thoughtcrime.securesms.jobmanager.requirements;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.thoughtcrime.securesms.jobmanager.dependencies.ContextDependent;

/**
 * A requirement that is satisfied when a network connection is present.
 */
public class NetworkRequirement extends SimpleRequirement implements ContextDependent {

  private transient Context context;

  public NetworkRequirement(Context context) {
    this.context = context;
  }

  public NetworkRequirement() {}

  @Override
  public boolean isPresent() {
    ConnectivityManager cm      = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo         netInfo = cm.getActiveNetworkInfo();

    return netInfo != null && netInfo.isConnected();
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
