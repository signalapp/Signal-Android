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
import android.net.Uri;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.MediaPreviewActivity.MediaPreviewAdapter;
import org.thoughtcrime.securesms.MediaPreviewActivity.MediaPreviewItem;
import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.components.ZoomingImageView.OnScaleChangedListener;
import org.thoughtcrime.securesms.crypto.MasterSecret;

/**
 * Adapter for providing a ViewPager with a draft image
 */
public class MediaPreviewDraftAdapter extends PagerAdapter implements MediaPreviewAdapter {

  private final MasterSecret masterSecret;
  private final Context      context;
  private final Uri          uri;
  private final String       type;
  private final long         date    = 0L;
  private final String       address = null;

  private OnScaleChangedListener scaleChangedListener;

  public MediaPreviewDraftAdapter(MasterSecret masterSecret, Context context, Uri uri, String type) {
    this.masterSecret = masterSecret;
    this.context      = context;
    this.uri          = uri;
    this.type         = type;
  }

  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    LayoutInflater   inflater  = ((Activity)context).getLayoutInflater();
    View             viewItem  = inflater.inflate(R.layout.media_preview_item, container, false);
    ZoomingImageView imageView = (ZoomingImageView) viewItem.findViewById(R.id.image);

    imageView.setImageUri(masterSecret, uri);
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

  @Override
  public MediaPreviewItem getItem(int position) {
    return new MediaPreviewItem(uri, type, date, address);
  }

  @Override
  public void setOnScaleChangedListener(OnScaleChangedListener scaleChangedListener) {
    this.scaleChangedListener = scaleChangedListener;
  }

  @Override
  public int getMediaPosition(Uri uri) {
    return 0;
  }

  @Override
  public int getCount() {
    return 1;
  }
}
