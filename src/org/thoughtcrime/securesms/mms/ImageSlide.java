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
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.LRUCache;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;

public class ImageSlide extends Slide {

  private static final int MAX_CACHE_SIZE = 10;
  private static final Map<Uri, SoftReference<Bitmap>> thumbnailCache =
      Collections.synchronizedMap(new LRUCache<Uri, SoftReference<Bitmap>>(MAX_CACHE_SIZE));

  public ImageSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }

  public ImageSlide(Context context, Uri uri) throws IOException {
    super(context, constructPartFromUri(context, uri));
  }
		
  @Override
  public Bitmap getThumbnail(int maxWidth, int maxHeight) {
    Bitmap thumbnail = getCachedThumbnail();

    if (thumbnail != null)
      return thumbnail;

    try {
      InputStream measureStream = getPartDataInputStream();
      InputStream dataStream    = getPartDataInputStream();

      thumbnail = BitmapUtil.createScaledBitmap(measureStream, dataStream, maxWidth, maxHeight);
      thumbnailCache.put(part.getDataUri(), new SoftReference<Bitmap>(thumbnail));

      return thumbnail;
    } catch (FileNotFoundException e) {
      Log.w("ImageSlide", e);
      return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_missing_thumbnail_picture);
    }
  }

  @Override
  public void setThumbnailOn(ImageView imageView) {
    Bitmap thumbnail = getCachedThumbnail();

    if (thumbnail != null) {
      Log.w("ImageSlide", "Setting cached thumbnail...");
      setThumbnailOn(imageView, thumbnail, true);
      return;
    }

    final ColorDrawable temporaryDrawable        = new ColorDrawable(Color.TRANSPARENT);
    final WeakReference<ImageView> weakImageView = new WeakReference<ImageView>(imageView);
    final Handler handler                        = new Handler();
    final int maxWidth                           = imageView.getWidth();
    final int maxHeight                          = imageView.getHeight();

    imageView.setImageDrawable(temporaryDrawable);

    MmsDatabase.slideResolver.execute(new Runnable() {
      @Override
      public void run() {
        final Bitmap bitmap         = getThumbnail(maxWidth, maxHeight);
        final ImageView destination = weakImageView.get();
        if (destination != null && destination.getDrawable() == temporaryDrawable) {
          handler.post(new Runnable() {
            @Override
            public void run() {
              setThumbnailOn(destination, bitmap, false);
            }
          });
        }
      }
    });
  }

  private void setThumbnailOn(ImageView imageView, Bitmap thumbnail, boolean fromMemory) {
    if (fromMemory) {
      imageView.setImageBitmap(thumbnail);
    } else {
      BitmapDrawable result           = new BitmapDrawable(context.getResources(), thumbnail);
      TransitionDrawable fadingResult = new TransitionDrawable(new Drawable[]{new ColorDrawable(Color.TRANSPARENT), result});
      imageView.setImageDrawable(fadingResult);
      fadingResult.startTransition(300);
    }
  }

  private Bitmap getCachedThumbnail() {
    synchronized (thumbnailCache) {
      SoftReference<Bitmap> bitmapReference = thumbnailCache.get(part.getDataUri());
      Log.w("ImageSlide", "Got soft reference: " + bitmapReference);

      if (bitmapReference != null) {
        Bitmap bitmap = bitmapReference.get();
        Log.w("ImageSlide", "Got cached bitmap: " + bitmap);
        if (bitmap != null) return bitmap;
        else                thumbnailCache.remove(part.getDataUri());
      }
    }

    return null;
  }

  @Override
  public boolean hasImage() {
    return true;
  }
	
  private static PduPart constructPartFromUri(Context context, Uri uri) throws IOException {
    PduPart part = new PduPart();
    byte[] data  = BitmapUtil.createScaledBytes(context, uri, 640, 480, (300 * 1024) - 5000);

    part.setData(data);
    part.setDataUri(uri);
    part.setContentType(ContentType.IMAGE_JPEG.getBytes());
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Image" + System.currentTimeMillis()).getBytes());

    return part;
  }
}
