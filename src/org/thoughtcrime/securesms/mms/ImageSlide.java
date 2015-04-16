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
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;

public class ImageSlide extends Slide {
  private static final String TAG = ImageSlide.class.getSimpleName();
  private boolean encrypted = false;

  public ImageSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }

  public ImageSlide(Context context, Uri uri) throws IOException, BitmapDecodingException {
    this(context, null, uri);
  }

  public ImageSlide(Context context, MasterSecret masterSecret, Uri uri) throws IOException, BitmapDecodingException {
    super(context, masterSecret, constructPartFromByteArrayAndUri(uri, decryptContent(uri, masterSecret), masterSecret != null));
    encrypted = masterSecret != null;
  }

  @Override
  public Uri getThumbnailUri() {
    if (getPart().getDataUri() != null) {
      return isDraft()
             ? getPart().getDataUri()
             : PartAuthority.getThumbnailUri(getPart().getPartId());
    }

    return null;
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return R.drawable.ic_missing_thumbnail_picture;
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean isEncrypted() {
    return encrypted;
  }

  private static byte[] decryptContent(Uri uri, MasterSecret masterSecret) {
    try {
      if (masterSecret != null) {
        InputStream inputStream = new DecryptingPartInputStream(new File(uri.getPath()), masterSecret);
        return Util.readFully(inputStream);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private static PduPart constructPartFromByteArrayAndUri(Uri uri, @Nullable byte[] data, boolean encrypted)
      throws IOException, BitmapDecodingException
  {
    PduPart part = new PduPart();

    part.setDataUri(uri);
    if (data != null)
      part.setData(data);
    part.setEncrypted(encrypted);
    part.setContentType(ContentType.IMAGE_JPEG.getBytes());
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Image" + System.currentTimeMillis()).getBytes());

    return part;
  }

}
