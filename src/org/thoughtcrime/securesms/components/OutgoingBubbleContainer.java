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
import android.content.res.TypedArray;
import android.support.annotation.DrawableRes;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.LayoutInflater;

import org.thoughtcrime.securesms.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OutgoingBubbleContainer extends BubbleContainer {
  private static final boolean[] CORNERS_MESSAGE_CAPTIONED = new boolean[]{true, false, true,  true};
  private static final boolean[] CORNERS_MEDIA_CAPTIONED   = new boolean[]{true, true,  false, true};
  private static final boolean[] CORNERS_ROUNDED           = new boolean[]{true, true,  true,  true};

  private static final int[] TRANSPORT_STYLE_ATTRIBUTES = new int[]{R.attr.conversation_item_sent_push_background,
                                                                    R.attr.conversation_item_sent_background,
                                                                    R.attr.conversation_item_sent_pending_background,
                                                                    R.attr.conversation_item_sent_push_pending_background};

  private static final int[] TRIANGLE_TICK_ATTRIBUTES = new int[]{R.attr.triangle_tick_outgoing_sent_push,
                                                                  R.attr.triangle_tick_outgoing_sent_sms,
                                                                  R.attr.triangle_tick_outgoing_pending_sms,
                                                                  R.attr.triangle_tick_outgoing_pending_push};

  private static final SparseIntArray TRANSPORT_STYLE_MAP = new SparseIntArray(TRANSPORT_STYLE_ATTRIBUTES.length) {{
    put(TRANSPORT_STATE_PUSH_SENT, 0);
    put(TRANSPORT_STATE_SMS_SENT, 1);
    put(TRANSPORT_STATE_SMS_PENDING, 2);
    put(TRANSPORT_STATE_PUSH_PENDING, 3);
  }};

  private TypedArray styledDrawables;
  private TypedArray triangleDrawables;

  @SuppressWarnings("UnusedDeclaration")
  public OutgoingBubbleContainer(Context context) {
    super(context);
  }

  @SuppressWarnings("UnusedDeclaration")
  public OutgoingBubbleContainer(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @SuppressWarnings("UnusedDeclaration")
  public OutgoingBubbleContainer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @SuppressWarnings("UnusedDeclaration")
  public OutgoingBubbleContainer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onCreateView() {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    inflater.inflate(R.layout.conversation_bubble_outgoing, this, true);

    this.styledDrawables   = getContext().obtainStyledAttributes(TRANSPORT_STYLE_ATTRIBUTES);
    this.triangleDrawables = getContext().obtainStyledAttributes(TRIANGLE_TICK_ATTRIBUTES  );
  }

  @Override
  protected int getForegroundColor(@TransportState int transportState) {
    return styledDrawables.getColor(TRANSPORT_STYLE_MAP.get(transportState), -1);
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
    return triangleDrawables.getResourceId(TRANSPORT_STYLE_MAP.get(transportState), -1);
  }
}
