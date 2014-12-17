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
import org.thoughtcrime.securesms.util.SmilUtil;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILRegionElement;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.util.Log;

public class AudioSlide extends Slide {
  private static final String TAG = AudioSlide.class.getSimpleName();

  public AudioSlide(Context context, PduPart part) {
    super(context, part);
  }

  public AudioSlide(Context context, Uri uri, String contentType) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri, contentType));
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
  public SMILRegionElement getSmilRegion(SMILDocument document) {
    return null;
  }

  @Override
  public SMILMediaElement getMediaElement(SMILDocument document) {
    return SmilUtil.createMediaElement("audio", document, new String(getPart().getName()));
  }

  @Override
  public Drawable getThumbnail(int maxWidth, int maxHeight) {
    return context.getResources().getDrawable(R.drawable.ic_menu_add_sound);
  }

  private static PduPart constructPartFromUri(Context context, Uri uri, String contentType)
      throws IOException, MediaTooLargeException {
    PduPart part  = new PduPart();

    assertMediaSize(context, uri);

    if (contentType == null || ContentType.isUnspecified(contentType))
      contentType = getContentTypeFromUri(context, uri, Audio.Media.MIME_TYPE);

    Log.w(TAG, "Setting mime type: " + contentType);
    part.setContentType(contentType.getBytes());

    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Audio" + System.currentTimeMillis()).getBytes());

    return part;
  }
}
