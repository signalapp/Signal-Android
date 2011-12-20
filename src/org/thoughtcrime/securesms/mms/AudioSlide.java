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
package org.thoughtcrime.securesms.mms;

import java.io.IOException;

import org.thoughtcrime.securesms.R;

import ws.com.google.android.mms.pdu.PduPart;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore.Audio;

public class AudioSlide extends Slide {

  public AudioSlide(Context context, PduPart part) {
    super(context, part);
  }

  public AudioSlide(Context context, Uri uri) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri));
  }
	
  @Override
    public boolean hasImage() {
    return true;
  }
	
  @Override
    public boolean hasAudio() {
    return true;
  }
	
  @Override
    public Bitmap getThumbnail() {
    return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_menu_add_sound);
  }
	
  public static PduPart constructPartFromUri(Context context, Uri uri) throws IOException, MediaTooLargeException {
    PduPart part = new PduPart();
		
    if (getMediaSize(context, uri) > MAX_MESSAGE_SIZE)
      throw new MediaTooLargeException("Audio track larger than size maximum.");
		
    Cursor cursor = null;
		
    try {
      cursor = context.getContentResolver().query(uri, new String[]{Audio.Media.MIME_TYPE}, null, null, null);
			
      if (cursor != null && cursor.moveToFirst())
	part.setContentType(cursor.getString(0).getBytes());
      else
	throw new IOException("Unable to query content type.");
    } finally {
      cursor.close();
    } 

    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Audio" + System.currentTimeMillis()).getBytes());
		
    return part;
  }
	
}
