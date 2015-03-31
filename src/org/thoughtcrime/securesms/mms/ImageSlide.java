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
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import com.bumptech.glide.BitmapRequestBuilder;
import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.BitmapDecodingException;

import java.io.IOException;
import java.io.InputStream;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;

public class ImageSlide extends Slide {
  private static final String TAG = ImageSlide.class.getSimpleName();

  public ImageSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }

  public ImageSlide(Context context, Uri uri) throws IOException, BitmapDecodingException {
    super(context, constructPartFromUri(uri));
  }

  @Override
  public GenericRequestBuilder loadThumbnail(Context context) {
    Glide.get(context).register(Uri.class, InputStream.class, new EncryptedStreamUriLoader.Factory(masterSecret));
    if (getPart().isPendingPush()) {
      return Glide.with(context).load(R.drawable.stat_sys_download);
    } else if (getPart().getDataUri() != null && getPart().getId() > -1) {
      return loadPartContent(context);
    } else if (getPart().getDataUri() != null) {
      return loadExternalContent(context);
    } else {
      return Glide.with(context).load(R.drawable.ic_missing_thumbnail_picture);
    }
  }

  private GenericRequestBuilder loadPartContent(Context context) {
    Pair<Integer,Integer> thumbDimens = getThumbnailDimens(getPart());
    GenericRequestBuilder builder = Glide.with(context)
                                         .load(PartAuthority.getThumbnailUri(getPart().getId()))
                                         .centerCrop()
                                         .crossFade()
                                         .error(R.drawable.ic_missing_thumbnail_picture);
    if (thumbDimens.first > 0 && thumbDimens.second > 0) {
      builder.override(thumbDimens.first, thumbDimens.second);
    }
    return builder;
  }

  private BitmapRequestBuilder loadExternalContent(Context context) {
    return Glide.with(context).load(getPart().getDataUri())
                              .asBitmap()
                              .fitCenter()
                              .listener(new PduThumbnailSetListener(getPart()))
                              .error(R.drawable.ic_missing_thumbnail_picture);
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  private static PduPart constructPartFromUri(Uri uri)
      throws IOException, BitmapDecodingException
  {
    PduPart part = new PduPart();

    part.setDataUri(uri);
    part.setContentType(ContentType.IMAGE_JPEG.getBytes());
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Image" + System.currentTimeMillis()).getBytes());

    return part;
  }

  private Pair<Integer,Integer> getThumbnailDimens(PduPart part) {
    int thumbnailHeight = context.getResources().getDimensionPixelSize(R.dimen.media_bubble_height);
    Log.w(TAG, "aspect ratio of " + part.getAspectRatio() + " for max height " + thumbnailHeight);
    if (part.getAspectRatio() < 1f) {
      return new Pair<>((int)(thumbnailHeight * part.getAspectRatio()), thumbnailHeight);
    } else {
      return new Pair<>(-1, -1);
    }
  }

  private static class PduThumbnailSetListener implements RequestListener<Uri, Bitmap> {
    private PduPart part;

    public PduThumbnailSetListener(@NonNull PduPart part) {
      this.part = part;
    }

    @Override
    public boolean onException(Exception e, Uri model, Target<Bitmap> target, boolean isFirstResource) {
      return false;
    }

    @Override
    public boolean onResourceReady(Bitmap resource, Uri model, Target<Bitmap> target, boolean isFromMemoryCache, boolean isFirstResource) {
      part.setThumbnail(resource);
      return false;
    }
  }
}
