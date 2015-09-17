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
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.R;

import java.io.IOException;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;

public class ImageSlide extends Slide {
  private static final String TAG = ImageSlide.class.getSimpleName();

  public ImageSlide(Context context, PduPart part) {
    super(context, part);
  }

  public ImageSlide(Context context, Uri uri, long size) throws IOException {
    super(context, constructPartFromUri(context, uri, ContentType.IMAGE_JPEG, size));
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

  @NonNull @Override public String getContentDescription() {
    return context.getString(R.string.Slide_image);
  }
}
