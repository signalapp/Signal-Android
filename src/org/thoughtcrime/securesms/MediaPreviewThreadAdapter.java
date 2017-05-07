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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CursorPagerAdapter;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.video.VideoPlayer;

/**
 * Adapter for providing a ViewPager with all media of a thread
 */
class MediaPreviewThreadAdapter extends CursorPagerAdapter implements OnPageChangeListener {
  private final static String TAG = MediaPreviewThreadAdapter.class.getSimpleName();

  private final Context                  context;
  private final Window                   window;
  private final MasterSecret             masterSecret;
  private final SparseArray<VideoPlayer> instantiatedVideos = new SparseArray<>();
  private       int                      activePosition;
  private       int                      directPlayPosition = -1;

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
                                                     getCursorAtReversedPositionOrThrow(position));
    final String      mediaType   = mediaRecord.getContentType();
    final Uri         mediaUri    = mediaRecord.getAttachment().getDataUri();

    Log.w(TAG, "Loading Part URI: " + mediaUri);

    if (mediaType != null && mediaType.startsWith("image/")) {
      image.setVisibility(View.VISIBLE);
      video.setVisibility(View.GONE);
      image.setImageUri(masterSecret, GlideApp.with(context), mediaUri, mediaType);
    } else if (mediaType != null && mediaType.startsWith("video/")) {
      image.setVisibility(View.GONE);
      video.setVisibility(View.VISIBLE);
      video.setWindow(window);
      video.setVideoSource(masterSecret, new VideoSlide(context, mediaRecord.getAttachment()));
      instantiatedVideos.put(position, video);
    }
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    ((ZoomingImageView) ((View) object).findViewById(R.id.image)).cleanup();
    ((VideoPlayer) ((View) object).findViewById(R.id.video_player)).cleanup(true);
    container.removeView((View) object);
    instantiatedVideos.remove(position);
  }

  @Override
  public boolean isViewFromObject(View view, Object object) {
    return view == object;
  }

  @Override
  public void onPageScrollStateChanged(int state) {}

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

  @Override
  public void onPageSelected(int position) {
    if (activePosition != position && instantiatedVideos.get(activePosition) != null) {
      instantiatedVideos.get(activePosition).hideVideo();
    }
    activePosition = position;
  }

  MediaRecord getMediaRecord(int position) {
    return MediaRecord.from(context, masterSecret, getCursorAtReversedPositionOrThrow(position));
  }

  int getAndSetStartPosition(Uri mediaUri) {
    int startPosition = -1;
    for (int i = getCount()-1; i >= 0; i--) {
      Uri dataUri = MediaRecord.from(context,
                                     masterSecret,
                                     getCursorAtReversedPositionOrThrow(i)).getAttachment()
                                                                           .getDataUri();
      if (dataUri != null && dataUri.equals(mediaUri)) {
        startPosition = i;
        break;
      }
    }
    return startPosition;
  }
}
