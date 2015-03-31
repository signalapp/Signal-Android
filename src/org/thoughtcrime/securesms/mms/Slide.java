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
import android.support.annotation.DrawableRes;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;

import ws.com.google.android.mms.pdu.PduPart;

public abstract class Slide {

  protected final PduPart      part;
  protected final Context      context;
  protected       MasterSecret masterSecret;

  public Slide(Context context, PduPart part) {
    this.part    = part;
    this.context = context;
  }

  public Slide(Context context, MasterSecret masterSecret, PduPart part) {
    this(context, part);
    this.masterSecret = masterSecret;
  }

  protected byte[] getPartData() {
    try {
      if (part.getData() != null)
        return part.getData();

      return Util.readFully(PartAuthority.getPartStream(context, masterSecret, part.getDataUri()));
    } catch (IOException e) {
      Log.w("Slide", e);
      return new byte[0];
    }
  }

  public String getContentType() {
    return new String(part.getContentType());
  }

  public Uri getUri() {
    return part.getDataUri();
  }

  public boolean hasImage() {
    return false;
  }

  public boolean hasVideo() {
    return false;
  }

  public boolean hasAudio() {
    return false;
  }

  public PduPart getPart() {
    return part;
  }

  public Uri getThumbnailUri() {
    return null;
  }

  public @DrawableRes int getPlaceholderRes(Theme theme) {
    throw new AssertionError("getPlaceholderRes() called for non-drawable slide");
  }

  public boolean isDraft() {
    return getPart().getId() < 0;
  }

  protected static void assertMediaSize(Context context, Uri uri)
      throws MediaTooLargeException, IOException
  {
    InputStream in = context.getContentResolver().openInputStream(uri);
    long   size    = 0;
    byte[] buffer  = new byte[512];
    int read;

    while ((read = in.read(buffer)) != -1) {
      size += read;
      if (size > MmsMediaConstraints.MAX_MESSAGE_SIZE) throw new MediaTooLargeException("Media exceeds maximum message size.");
    }
  }
}
