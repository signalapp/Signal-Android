/**
 * Copyright (C) 2014 Open Whisper Systems
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

package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;

import java.lang.ref.WeakReference;

/**
 * Runnable to load contact photos if they have them
 *
 * @author Jake McGinty
 */
public class BitmapWorkerRunnable implements Runnable {
  private final static String TAG = BitmapWorkerRunnable.class.getSimpleName();

  private final Bitmap defaultPhoto;

  private final WeakReference<ImageView> imageViewReference;
  private final Context                  context;
  private final int                      size;
  public final  String                   number;

  public BitmapWorkerRunnable(Context context, ImageView imageView, Bitmap defaultPhoto, String number, int size) {
    this.imageViewReference = new WeakReference<ImageView>(imageView);
    this.context = context;
    this.defaultPhoto = defaultPhoto;
    this.size = size;
    this.number = number;
  }

  @Override
  public void run() {
    final Bitmap bitmap;

    final Recipient recipient = RecipientFactory.getRecipientsFromString(context, number, false).getPrimaryRecipient();
    final Bitmap contactPhoto = recipient.getContactPhoto();
    if (defaultPhoto == contactPhoto) {
      return;
    }

    bitmap = BitmapUtil.getCircleBitmap(contactPhoto);

    if (bitmap != null) {
      final ImageView imageView                  = imageViewReference.get();
      final TaggedFutureTask<?> bitmapWorkerTask = AsyncDrawable.getBitmapWorkerTask(imageView);

      if (bitmapWorkerTask.getTag().equals(number) && imageView != null) {
        final BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        imageView.post(new Runnable() {
          @Override
          public void run() {
            imageView.setImageDrawable(drawable);
          }
        });
      }
    }
  }

  public static class AsyncDrawable extends BitmapDrawable {
    private final WeakReference<TaggedFutureTask<?>> bitmapWorkerTaskReference;

    public AsyncDrawable(Resources res, Bitmap bitmap,
                         TaggedFutureTask<?> bitmapWorkerTask) {
      super(res, bitmap);
      bitmapWorkerTaskReference =
          new WeakReference<TaggedFutureTask<?>>(bitmapWorkerTask);
    }

    public TaggedFutureTask<?> getBitmapWorkerTask() {
      return bitmapWorkerTaskReference.get();
    }

    public static TaggedFutureTask<?> getBitmapWorkerTask(ImageView imageView) {
      if (imageView != null) {
        final Drawable drawable = imageView.getDrawable();
        if (drawable instanceof AsyncDrawable) {
          final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
          return asyncDrawable.getBitmapWorkerTask();
        }
      }
      return null;
    }
  }

}
