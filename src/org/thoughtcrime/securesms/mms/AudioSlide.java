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
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.ResUtil;

import ws.com.google.android.mms.pdu.PduPart;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.util.Pair;

public class AudioSlide extends Slide {

  public AudioSlide(Context context, Uri uri) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri));
  }

  public AudioSlide(Context context, MasterSecret masterSecret, Uri uri)
      throws IOException, MediaTooLargeException
  {
    super(context, masterSecret, constructPartFromUri(context, uri));
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
  public ListenableFutureTask<Pair<Drawable,Boolean>> getThumbnail(Context context) {
    return new ListenableFutureTask<>(new Pair<>(ResUtil.getDrawable(context, R.attr.conversation_icon_attach_audio), true));
  }

  public static PduPart constructPartFromUri(Context context, Uri uri) throws IOException, MediaTooLargeException {
    PduPart part = new PduPart();

    if (isAuthorityPartDb(uri)) {
      part.setContentType(getContentTypeForPartInDb(context, uri));
    } else {
      assertMediaSize(context, uri);
      part.setContentType(getContentTypeForPartOutsideDb(context, uri, Audio.Media.MIME_TYPE));
    }

    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Audio" + System.currentTimeMillis()).getBytes());

    return part;
  }
}
