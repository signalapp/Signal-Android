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

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.thoughtcrime.securesms.components.ZoomingImageView.OnScaleChangedListener;
import org.thoughtcrime.securesms.MediaPreviewActivity.MediaPreviewAdapter;

/**
 * ViewPager allowing to temporarily disable paging
 */
public class MediaPreviewViewPager extends ViewPager {
  private boolean pagingEnabled = true;

  public MediaPreviewViewPager(Context context) {
    super(context);
  }

  public MediaPreviewViewPager(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return this.pagingEnabled && super.onTouchEvent(event);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    try {
      return this.pagingEnabled && super.onInterceptTouchEvent(event);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public void setAdapter(PagerAdapter adapter) {
    if (adapter != null) ((MediaPreviewAdapter) adapter).setOnScaleChangedListener(new ScaleChangedListener());
    super.setAdapter(adapter);
  }

  private class ScaleChangedListener implements OnScaleChangedListener {
    @Override
    public void onScaleChanged(float scale) {
      pagingEnabled = (scale == 1.0);
    }
  }
}
