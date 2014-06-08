/**
 * Copyright (C) 2012 Whisper Systems
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
package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.util.List;

public abstract class ContactIdentityManager {

  public static ContactIdentityManager getInstance(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
      return new ContactIdentityManagerICS(context);
    else
      return new ContactIdentityManagerGingerbread(context);
  }

  protected final Context context;

  public ContactIdentityManager(Context context) {
    this.context = context.getApplicationContext();
  }

  public abstract Uri        getSelfIdentityUri();
  public abstract boolean    isSelfIdentityAutoDetected();
  public abstract List<Long> getSelfIdentityRawContactIds();

}
