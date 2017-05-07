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
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CursorPagerAdapter;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.video.VideoPlayer;

import java.io.IOException;

/**
 * Adapter for providing a ViewPager with all media of a thread
 */
class MediaPreviewThreadAdapter extends CursorPagerAdapter {
  private final static String TAG = MediaPreviewThreadAdapter.class.getSimpleName();

  private final Context      context;
  private final Window       window;
  private final MasterSecret masterSecret;

  MediaPreviewThreadAdapter(Context context, Window window, MasterSecret masterSecret, Cursor cursor) {
    super(cursor);
    this.context      = context;
    this.window       = window;
    this.masterSecret = masterSecret;
  }

  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    final View             view  = LayoutInflater.from(context)
                                                 .inflate(R.layout.media_preview_page,
                                                          container,
                                                          false);
    final ZoomingImageView image = (ZoomingImageView) view.findViewById(R.id.image);
    final VideoPlayer      video = (VideoPlayer) view.findViewById(R.id.video_player);

    setMedia(image, video, position);
    container.addView(view);
    return view;
  }

  private void setMedia(ZoomingImageView image, VideoPlayer video, int position) {
    final MediaRecord mediaRecord = MediaRecord.from(context,
                                                     masterSecret,
                                                     getCursorAtPositionOrThrow(position));
    final String      mediaType   = mediaRecord.getContentType();
    final Uri         mediaUri    = mediaRecord.getAttachment().getDataUri();
    final long        size        = mediaRecord.getAttachment().getSize();

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
      Toast.makeText(context.getApplicationContext(),
                     R.string.MediaPreviewActivity_unssuported_media_type,
                     Toast.LENGTH_LONG).show();
      ((Activity)context).finish();
    }
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    ((ZoomingImageView) ((View) object).findViewById(R.id.image)).cleanup();
    ((VideoPlayer) ((View) object).findViewById(R.id.video_player)).cleanup();
    container.removeView((View) object);
  }

  @Override
  public boolean isViewFromObject(View view, Object object) {
    return view == object;
  }

  MediaRecord getMediaRecord(int position) {
    return MediaRecord.from(context, masterSecret, getCursorAtPositionOrThrow(position));
  }

  int getAndSetStartPosition(Uri mediaUri) {
    int startPosition = -1;
    for (int i = 0; i < getCount(); i++) {
      Uri dataUri = MediaRecord.from(context,
                                     masterSecret,
                                     getCursorAtPositionOrThrow(i)).getAttachment()
                                                                   .getDataUri();
      if (dataUri != null && dataUri.equals(mediaUri)) {
        startPosition = i;
        break;
      }
    }
    return startPosition;
  }
}
