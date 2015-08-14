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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.DrawableRes;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ResUtil;

import java.io.IOException;

import ws.com.google.android.mms.pdu.PduPart;

public class VideoSlide extends Slide {

  public VideoSlide(Context context, Uri uri) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri));
  }

  public VideoSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return ResUtil.getDrawableRes(theme, R.attr.conversation_icon_attach_video);
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasVideo() {
    return true;
  }

  private static PduPart constructPartFromUri(Context context, Uri uri)
      throws IOException, MediaTooLargeException
  {
    PduPart         part     = new PduPart();
    ContentResolver resolver = context.getContentResolver();
    Cursor          cursor   = null;
    String          contentType = null;

    try {
      cursor = resolver.query(uri, new String[] {MediaStore.Video.Media.MIME_TYPE}, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
         contentType = cursor.getString(0);
         Log.i("VideoSlide", "Setting mime type: " + contentType);
         part.setContentType(contentType.getBytes());
       }

       // fallback:
       // query above does not give result for media set via ShareActivity (external share intents with files)
       if (contentType == null) {
         contentType = MediaUtil.getMimeTyp(uri);
         if (contentType != null) {
           Log.i("VideoSlide", "Setting mime type: " + contentType);
           part.setContentType(contentType.getBytes());
         }
         else
           throw new IOException("Unable to query content type.");
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    assertMediaSize(context, uri, MmsMediaConstraints.MAX_MESSAGE_SIZE);
    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Video" + System.currentTimeMillis()).getBytes());

    return part;
  }
}
