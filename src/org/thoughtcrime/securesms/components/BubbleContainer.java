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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ResUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

public abstract class BubbleContainer extends RelativeLayout {
  @SuppressWarnings("unused")
  private static final String TAG = BubbleContainer.class.getSimpleName();

  public static final int TRANSPORT_STATE_PUSH_SENT    = 0;
  public static final int TRANSPORT_STATE_SMS_SENT     = 1;
  public static final int TRANSPORT_STATE_SMS_PENDING  = 2;
  public static final int TRANSPORT_STATE_PUSH_PENDING = 3;

  public static final int MEDIA_STATE_NO_MEDIA    = 0;
  public static final int MEDIA_STATE_CAPTIONLESS = 1;
  public static final int MEDIA_STATE_CAPTIONED   = 2;

  @IntDef({TRANSPORT_STATE_PUSH_SENT, TRANSPORT_STATE_PUSH_PENDING, TRANSPORT_STATE_SMS_SENT, TRANSPORT_STATE_SMS_PENDING})
  public @interface TransportState {}

  @IntDef({MEDIA_STATE_NO_MEDIA, MEDIA_STATE_CAPTIONLESS, MEDIA_STATE_CAPTIONED})
  public @interface MediaState {}

  private View          bodyBubble;
  private View          triangleTick;
  private ThumbnailView media;
  private int           shadowColor;
  private int           mmsPendingOverlayColor;

  public BubbleContainer(Context context) {
    super(context);
    initialize();
  }

  public BubbleContainer(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public BubbleContainer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  public BubbleContainer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  protected abstract void onCreateView();
  protected abstract int getForegroundColor(@TransportState int transportState);
  protected abstract boolean[] getMessageCorners(@MediaState int mediaState);
  protected abstract boolean[] getMediaCorners(@MediaState int mediaState);
  protected abstract int getTriangleTickRes(@TransportState int transportState);

  protected void initialize() {
    onCreateView();
    this.bodyBubble   = findViewById(R.id.body_bubble  );
    this.triangleTick = findViewById(R.id.triangle_tick);
    this.media        = (ThumbnailView) findViewById(R.id.image_view);

    this.shadowColor            = ResUtil.getColor(getContext(), R.attr.conversation_item_shadow);
    this.mmsPendingOverlayColor = ResUtil.getColor(getContext(), R.attr.conversation_item_mms_pending_mask);
  }

  public void setState(@TransportState int transportState, @MediaState int mediaState) {
    updateBodyBubble(transportState, mediaState);
    if (isMediaPresent(mediaState)) {
      updateMediaBubble(transportState, mediaState);
    }
    setMediaVisibility(mediaState);
    setAlignment(mediaState);
    setMediaPendingMask(transportState);
  }

  private void updateBodyBubble(@TransportState int transportState, @MediaState int mediaState) {
    final boolean               hasShadow = mediaState == MEDIA_STATE_CAPTIONED || mediaState == MEDIA_STATE_NO_MEDIA;
    final BubbleDrawableBuilder builder   = new BubbleDrawableBuilder();
    final int                   color     = getForegroundColor(transportState);

    final Drawable bodyDrawable = builder.setColor(color)
                                         .setShadowColor(shadowColor)
                                         .setCorners(getMessageCorners(mediaState))
                                         .setHasShadow(hasShadow)
                                         .create(getContext());
    ViewUtil.setBackgroundSavingPadding(triangleTick, getTriangleTickRes(transportState));
    ViewUtil.setBackgroundSavingPadding(bodyBubble, bodyDrawable);
  }

  private void updateMediaBubble(@TransportState int transportState, @MediaState int mediaState) {
    final int                   foregroundColor = getForegroundColor(transportState);
    final BubbleDrawableBuilder builder         = new BubbleDrawableBuilder();

    final Drawable mediaDrawable = builder.setColor(foregroundColor)
                                          .setShadowColor(shadowColor)
                                          .setCorners(getMediaCorners(mediaState))
                                          .setHasShadow(false)
                                          .create(getContext());
    ViewUtil.setBackgroundSavingPadding(media, mediaDrawable);
    media.setBorderColor(foregroundColor);
  }

  private void setMediaVisibility(@MediaState int mediaState) {
    media.reset();
    if (!isMediaPresent(mediaState)) {
      media.hide();
    }
  }

  private void setMediaPendingMask(@TransportState int transportState) {
    if (isPending(transportState)) {
      media.setForeground(new ColorDrawable(mmsPendingOverlayColor));
    } else {
      media.setForeground(new ColorDrawable(Color.TRANSPARENT));
    }
  }

  private void setAlignment(@MediaState int mediaState) {
    RelativeLayout.LayoutParams parentParams = (RelativeLayout.LayoutParams) bodyBubble.getLayoutParams();
    if (mediaState == MEDIA_STATE_CAPTIONLESS) {
      parentParams.addRule(RelativeLayout.BELOW, 0);
      parentParams.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.thumbnail_container);
    } else if (mediaState == MEDIA_STATE_CAPTIONED) {
      parentParams.addRule(RelativeLayout.BELOW, R.id.thumbnail_container);
      parentParams.addRule(RelativeLayout.ALIGN_BOTTOM, 0);
    } else {
      parentParams.addRule(RelativeLayout.BELOW, 0);
      parentParams.addRule(RelativeLayout.ALIGN_BOTTOM, 0);
    }
    bodyBubble.setLayoutParams(parentParams);
  }

  private boolean isMediaPresent(@MediaState int mediaState) {
    return mediaState != MEDIA_STATE_NO_MEDIA;
  }

  private boolean isPending(@TransportState int transportState) {
    return transportState == TRANSPORT_STATE_PUSH_PENDING || transportState == TRANSPORT_STATE_SMS_PENDING;
  }
}