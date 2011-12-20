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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.util.Log;

public class ImageSlide extends Slide {

  private static final int MAX_CACHE_SIZE = 10;
	
  private static final LinkedHashMap<Uri,SoftReference<Bitmap>> thumbnailCache = new LinkedHashMap<Uri,SoftReference<Bitmap>>() {
    @Override
    protected boolean removeEldestEntry(Entry<Uri,SoftReference<Bitmap>> eldest) {
      return this.size() > MAX_CACHE_SIZE;
    }
  };

  public ImageSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }

  public ImageSlide(Context context, Uri uri) throws IOException {
    super(context, constructPartFromUri(context, uri));
  }
		
  @Override
    public Bitmap getThumbnail() {
    if (thumbnailCache.containsKey(part.getDataUri())) {
      Log.w("ImageSlide", "Cached thumbnail...");
      Bitmap bitmap = thumbnailCache.get(part.getDataUri()).get();
      if (bitmap != null) return bitmap;
      else                thumbnailCache.remove(part.getDataUri());				
    }

    try {
      BitmapFactory.Options options = getImageDimensions(getPartDataInputStream());
      int imageWidth                = options.outWidth;
      int imageHeight               = options.outHeight;
			
      int scaler = 1;
      while ((imageWidth / scaler > 480) || (imageHeight / scaler > 480)) 
	scaler *= 2;
			
      options.inSampleSize       = scaler;
      options.inJustDecodeBounds = false;
						
      Bitmap thumbnail = BitmapFactory.decodeStream(getPartDataInputStream(), null, options);
      thumbnailCache.put(part.getDataUri(), new SoftReference<Bitmap>(thumbnail));

      return thumbnail;
    } catch (FileNotFoundException fnfe) {
      Log.w("ImageSlide", fnfe);
      return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_missing_thumbnail_picture);
    }
  }
	
  private static BitmapFactory.Options getImageDimensions(InputStream inputStream) throws FileNotFoundException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds    = true;
    BitmapFactory.decodeStream(inputStream, null, options);

    return options;
  }
	
  private static BitmapFactory.Options getImageDimensions(Context context, Uri uri) throws FileNotFoundException {
    InputStream in = context.getContentResolver().openInputStream(uri);
    return getImageDimensions(in);
  }
		
  @Override
    public boolean hasImage() {
    return true;
  }
	
  private static PduPart constructPartFromUri(Context context, Uri uri) throws IOException {
    PduPart part = new PduPart();
	
    BitmapFactory.Options options = getImageDimensions(context, uri);
    long size                     = getMediaSize(context, uri);
		
    if (options.outWidth > 640 || options.outHeight > 480 || size > (1024*1024)) {
      byte[] data = scaleImage(context, uri, options, size, 640, 480, 1024*1024);
      part.setData(data);
      Log.w("ImageSlide", "Setting actual part data...");
    }
		
    Log.w("ImageSlide", "Setting part data URI..");
    part.setDataUri(uri);
    part.setContentType(ContentType.IMAGE_JPEG.getBytes());
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Image" + System.currentTimeMillis()).getBytes());

    return part;
  }
	
  private static byte[] scaleImage(Context context, Uri uri, BitmapFactory.Options options, long size, int maxWidth, int maxHeight, int maxSize) throws FileNotFoundException {
    int scaler = 1;
    while ((options.outWidth / scaler > maxWidth) || (options.outHeight / scaler > maxHeight)) 
      scaler *= 2;
		
    options.inSampleSize       = scaler;
    options.inJustDecodeBounds = false;
					
    Bitmap bitmap              = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri), null, options);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int quality                = 80;
		
    do {
      bitmap.compress(CompressFormat.JPEG, quality, baos);
      if (baos.size() > maxSize)
	quality = quality * maxSize / baos.size();
    } while (baos.size() > maxSize);

    return baos.toByteArray();
  }
}
