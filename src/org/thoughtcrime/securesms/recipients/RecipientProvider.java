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
package org.thoughtcrime.securesms.recipients;

import org.thoughtcrime.securesms.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

public abstract class RecipientProvider {
	
  private static Bitmap defaultContactPhoto;
	
  public abstract Recipient getRecipient(Context context, String number);
  public abstract Recipient getRecipient(Context context, Uri uri);
  public abstract void viewContact(Context context, Recipient recipient);
  public abstract void addContact(Context context, Recipient recipient);

  public Bitmap getDefaultContactPhoto(Context context) {
    synchronized (this) {
      if (defaultContactPhoto == null)
        defaultContactPhoto =  BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact_picture);
    }
		
    return defaultContactPhoto;
  }
}