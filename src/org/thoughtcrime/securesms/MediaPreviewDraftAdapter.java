/**
 * Copyright (C) 2017 Open Whisper Systems
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.video.VideoPlayer;

import java.io.IOException;

/**
 * Adapter for providing a ViewPager with a media draft
 */
class MediaPreviewDraftAdapter extends PagerAdapter {
  private final static String TAG = MediaPreviewDraftAdapter.class.getSimpleName();

  private final Context      context;
  private final Window       window;
  private final MasterSecret masterSecret;
  private final Uri          mediaUri;
  private final String       mediaType;
  private final long         size;

  MediaPreviewDraftAdapter(Context      context,
                           Window       window,
                           MasterSecret masterSecret,
                           Uri          mediaUri,
                           String       mediaType,
                           long         size) {
    this.context      = context;
    this.window       = window;
    this.masterSecret = masterSecret;
    this.mediaUri     = mediaUri;
    this.mediaType    = mediaType;
    this.size         = size;
  }

  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    final View             view  = LayoutInflater.from(context)
                                                 .inflate(R.layout.media_preview_page,
                                                          container,
                                                          false);
    final ZoomingImageView image = (ZoomingImageView) view.findViewById(R.id.image);
    final VideoPlayer      video = (VideoPlayer) view.findViewById(R.id.video_player);

    setMedia(image, video);
    container.addView(view);
    return view;
  }

  private void setMedia(ZoomingImageView image, VideoPlayer video) {
    Log.w(TAG, "Loading Part URI: " + mediaUri);

    try {
      if (mediaType != null && mediaType.startsWith("image/")) {
        image.setVisibility(View.VISIBLE);
        video.setVisibility(View.GONE);
        image.setImageUri(masterSecret, GlideApp.with(context), mediaUri, mediaType);
      } else if (mediaType != null && mediaType.startsWith("video/")) {
        image.setVisibility(View.GONE);
        video.setVisibility(View.VISIBLE);
        video.setWindow(window);
        video.setVideoSource(masterSecret, new VideoSlide(context, mediaUri, size));
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      Toast.makeText(context, R.string.MediaPreviewActivity_unssuported_media_type, Toast.LENGTH_LONG).show();
      ((Activity) context).finish();
    }
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    ((ZoomingImageView) ((View) object).findViewById(R.id.image)).cleanup();
    ((VideoPlayer) ((View) object).findViewById(R.id.video_player)).cleanup();
    container.removeView((View) object);
  }

  @Override
  public int getCount() {
    return 1;
  }

  @Override
  public boolean isViewFromObject(View view, Object object) {
    return view == object;
  }
}
