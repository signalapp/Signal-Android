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
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.support.annotation.DrawableRes;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILRegionElement;


import java.io.IOException;


import ws.com.google.android.mms.pdu.PduPart;

public class AudioSlide extends Slide {

  private static final String TAG = AudioSlide.class.getSimpleName();

  public AudioSlide(Context context, Uri uri, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri, sendOrReceive));
  }
  public AudioSlide(Context context, Uri uri, String contentType, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri, contentType, sendOrReceive));
  }
  @Override
  public boolean hasImage() {
    return true;
  }
  @Override
  public boolean hasAudio() {
    return true;
  }
  public AudioSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }
  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return R.drawable.mms_play_btn;
  }

  @Override
  public SMILRegionElement getSmilRegion(SMILDocument document) {
    return null;
  }

  @Override
  public SMILMediaElement getMediaElement(SMILDocument document) {
    return null;
  }

public static PduPart constructPartFromUri(Context context, Uri uri, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    PduPart part = new PduPart();

    assertMediaSize(context, uri, sendOrReceive);

    part.setContentType(getContentTypeFromUri(context, uri, Audio.Media.MIME_TYPE).getBytes());

    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Audio" + System.currentTimeMillis()).getBytes());

    return part;
  }
  public static PduPart constructPartFromUri(Context context, Uri uri, String contentType, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    PduPart part = new PduPart();

    assertMediaSize(context, uri, sendOrReceive);

    part.setContentType(contentType.getBytes());

    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Audio" + System.currentTimeMillis()).getBytes());

    return part;
  }
}
