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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.providers.PartProvider;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import ws.com.google.android.mms.pdu.PduPart;

public abstract class Slide {

  protected static final int MAX_MESSAGE_SIZE = 1048576;
	
  protected final PduPart part;
  protected final Context context;
  protected MasterSecret masterSecret;
	
  public Slide(Context context, PduPart part) {
    this.part    = part;
    this.context = context;
  }
	
  public Slide(Context context, MasterSecret masterSecret, PduPart part) {
    this(context, part);
    this.masterSecret = masterSecret;
  }
	
  public InputStream getPartDataInputStream() throws FileNotFoundException {
    Uri partUri = part.getDataUri();
		
    Log.w("Slide", "Loading Part URI: " + partUri);
		
    if (PartProvider.isAuthority(partUri))
      return DatabaseFactory.getEncryptingPartDatabase(context, masterSecret).getPartStream(ContentUris.parseId(partUri));
    else
      return context.getContentResolver().openInputStream(partUri);
  }
	
  protected static long getMediaSize(Context context, Uri uri) throws IOException {
    InputStream in = context.getContentResolver().openInputStream(uri);
    long size      = 0;
    byte[] buffer  = new byte[512];
    int read;
		
    while ((read = in.read(buffer)) != -1)
      size += read;
		
    return size;
  }
	
  protected byte[] getPartData() {
    if (part.getData() != null)
      return part.getData();
		
    long partId = ContentUris.parseId(part.getDataUri());
    return DatabaseFactory.getEncryptingPartDatabase(context, masterSecret).getPart(partId, true).getData();
  }
	
  public String getContentType() {
    return new String(part.getContentType());
  }
	
  public Uri getUri() {
    return part.getDataUri();
  }
	
  public Bitmap getThumbnail() {
    throw new AssertionError("getThumbnail() called on non-thumbnail producing slide!");
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
}
