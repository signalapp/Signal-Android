/**
 * Copyright (C) 2016 Open Whisper Systems
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
package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.components.ZoomingImageView.OnScaleChangedListener;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CursorPagerAdapter;
import org.thoughtcrime.securesms.database.ImageDatabase.ImageRecord;

/**
 * Adapter for providing a ViewPager with all images of a thread
 */
public class MediaPreviewThreadAdapter extends CursorPagerAdapter {
  private final Context          context;
  private final MasterSecret     masterSecret;
  private OnScaleChangedListener scaleChangedListener;

  public MediaPreviewThreadAdapter(Context context, MasterSecret masterSecret, Cursor cursor) {
    super(cursor);
    this.context      = context;
    this.masterSecret = masterSecret;
  }

  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    LayoutInflater   inflater    = ((Activity)context).getLayoutInflater();
    View             viewItem    = inflater.inflate(R.layout.media_preview_item, container, false);
    ZoomingImageView imageView   = (ZoomingImageView) viewItem.findViewById(R.id.image);
    ImageRecord      imageRecord = ImageRecord.from(getCursorAtPositionOrThrow(reverse(position)));

    imageView.setImageUri(masterSecret, imageRecord.getAttachment().getDataUri());
    imageView.setOnScaleChangedListener(scaleChangedListener);
    container.addView(viewItem);

    return viewItem;
  }

  @Override
  public boolean isViewFromObject(View view, Object object) {
    return view == object;
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    ((ZoomingImageView) ((View) object).findViewById(R.id.image)).cleanupPhotoViewAttacher();
    container.removeView((View) object);
  }

  public int getImagePosition(Uri uri) {
    int imagePosition = -1;
    for (int i = 0; i < getCount(); i++) {
      Uri dataUri = ImageRecord.from(getCursorAtPositionOrThrow(i)).getAttachment().getDataUri();
      if (dataUri != null && dataUri.equals(uri)) {
        imagePosition = i;
        break;
      }
    }
    return reverse(imagePosition);
  }

  public ImageRecord getImageAtPosition(int position) {
    return ImageRecord.from(getCursorAtPositionOrThrow(reverse(position)));
  }

  public void setOnScaleChangedListener(OnScaleChangedListener scaleChangedListener) {
    this.scaleChangedListener = scaleChangedListener;
  }

  private int reverse(int position) {
    return getCount()-1-position;
  }
}
