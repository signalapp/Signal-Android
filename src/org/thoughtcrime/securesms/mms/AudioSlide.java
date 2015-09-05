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

import android.content.Context;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.support.annotation.DrawableRes;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.ResUtil;

import java.io.IOException;

import ws.com.google.android.mms.pdu.PduPart;

public class AudioSlide extends Slide {

  public AudioSlide(Context context, Uri uri) throws IOException {
    super(context, constructPartFromUri(context, uri));
  }

  public AudioSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
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
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return ResUtil.getDrawableRes(theme, R.attr.conversation_icon_attach_audio);
  }

  public static PduPart constructPartFromUri(Context context, Uri uri) throws IOException {
    PduPart part = new PduPart();

    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(uri, new String[]{Audio.Media.MIME_TYPE}, null, null, null);

      if (cursor != null && cursor.moveToFirst())
        part.setContentType(cursor.getString(0).getBytes());
      else
        throw new IOException("Unable to query content type.");
    } finally {
      if (cursor != null)
        cursor.close();
    }

    part.setDataSize(getMediaSize(context, null, uri));
    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Audio" + System.currentTimeMillis()).getBytes());

    return part;
  }
}
