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
import java.io.InputStream;

import org.thoughtcrime.securesms.util.Util;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILRegionElement;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import ws.com.google.android.mms.pdu.PduPart;

public abstract class Slide {

  public static final int MAX_MESSAGE_SIZE = 280 * 1024;

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

  public Drawable getThumbnail(int maxWidth, int maxHeight) {
    throw new AssertionError("getThumbnail() called on non-thumbnail producing slide!");
  }

  public void setThumbnailOn(ImageView imageView) {
    imageView.setImageDrawable(getThumbnail(imageView.getWidth(), imageView.getHeight()));
  }

  public Bitmap getGeneratedThumbnail() { return null; }

  public boolean hasImage() {
    return false;
  }

  public boolean hasVideo() {
    return false;
  }

  public boolean hasAudio() {
    return false;
  }

  public Bitmap getImage() {
    throw new AssertionError("getImage() called on non-image slide!");
  }

  public boolean hasText() {
    return false;
  }

  public String getText() {
    throw new AssertionError("getText() called on non-text slide!");
  }

  public PduPart getPart() {
    return part;
  }

  public abstract SMILRegionElement getSmilRegion(SMILDocument document);

  public abstract SMILMediaElement getMediaElement(SMILDocument document);

  protected static void assertMediaSize(Context context, Uri uri)
      throws MediaTooLargeException, IOException
  {
    InputStream in = context.getContentResolver().openInputStream(uri);
    long   size    = 0;
    byte[] buffer  = new byte[512];
    int read;

    while ((read = in.read(buffer)) != -1) {
      size += read;
      if (size > MAX_MESSAGE_SIZE) throw new MediaTooLargeException("Media exceeds maximum message size.");
    }
  }
}
