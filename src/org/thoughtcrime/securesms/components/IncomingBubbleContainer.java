/**
 * Copyright (C) 2015 Open Whisper Systems
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
package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ResUtil;

public class IncomingBubbleContainer extends BubbleContainer {
  private static final String TAG = IncomingBubbleContainer.class.getSimpleName();

  private static final boolean[] CORNERS_MESSAGE_CAPTIONED = new boolean[]{false, true, true, true };
  private static final boolean[] CORNERS_MEDIA_CAPTIONED   = new boolean[]{true,  true, true, false};
  private static final boolean[] CORNERS_ROUNDED           = new boolean[]{true,  true, true, true };

  private int foregroundColor;
  private int triangleTickRes;

  @SuppressWarnings("UnusedDeclaration")
  public IncomingBubbleContainer(Context context) {
    super(context);
  }

  @SuppressWarnings("UnusedDeclaration")
  public IncomingBubbleContainer(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @SuppressWarnings("UnusedDeclaration")
  public IncomingBubbleContainer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @SuppressWarnings("UnusedDeclaration")
  public IncomingBubbleContainer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onCreateView() {
    Log.w(TAG, "onCreateView()");
    LayoutInflater inflater = LayoutInflater.from(getContext());
    inflater.inflate(R.layout.conversation_bubble_incoming, this, true);

    this.foregroundColor = ResUtil.getColor(getContext(), R.attr.conversation_item_received_background);
    this.triangleTickRes = ResUtil.getDrawableRes(getContext(), R.attr.triangle_tick_incoming);
  }

  @Override
  protected int getForegroundColor(@TransportState int transportState) {
    return foregroundColor;
  }

  @Override
  protected boolean[] getMessageCorners(@MediaState int mediaState) {
    return mediaState == MEDIA_STATE_CAPTIONED ? CORNERS_MESSAGE_CAPTIONED : CORNERS_ROUNDED;
  }

  @Override
  protected boolean[] getMediaCorners(@MediaState int mediaState) {
    return mediaState == MEDIA_STATE_CAPTIONED ? CORNERS_MEDIA_CAPTIONED : CORNERS_ROUNDED;
  }

  @Override
  protected int getTriangleTickRes(@TransportState int transportState) {
    return triangleTickRes;
  }
}
