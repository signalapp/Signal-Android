/** 
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy;
import org.thoughtcrime.securesms.util.MediaUtil;

public class VideoSlide extends Slide {

  public VideoSlide(Context context, Uri uri, long dataSize, boolean gif) {
    this(context, uri, dataSize, gif, null, null);
  }

  public VideoSlide(Context context, Uri uri, long dataSize, boolean gif, @Nullable String caption, @Nullable AttachmentTable.TransformProperties transformProperties) {
    super(context, constructAttachmentFromUri(context, uri, MediaUtil.VIDEO_UNSPECIFIED, dataSize, 0, 0, MediaUtil.hasVideoThumbnail(context, uri), null, caption, null, null, null, false, false, gif, false, transformProperties));
  }

  public VideoSlide(Context context, Uri uri, long dataSize, boolean gif, int width, int height, @Nullable String caption, @Nullable AttachmentTable.TransformProperties transformProperties) {
    super(context, constructAttachmentFromUri(context, uri, MediaUtil.VIDEO_UNSPECIFIED, dataSize, width, height, MediaUtil.hasVideoThumbnail(context, uri), null, caption, null, null, null, false, false, gif, false, transformProperties));
  }

  public VideoSlide(Context context, Attachment attachment) {
    super(context, attachment);
  }

  @Override
  public boolean hasPlaceholder() {
    return true;
  }

  @Override
  public boolean hasPlayOverlay() {
    return !(isVideoGif() && GiphyMp4PlaybackPolicy.autoplay()) || Build.VERSION.SDK_INT < 23;
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return R.drawable.ic_video;
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasVideo() {
    return true;
  }

  @NonNull @Override
  public String getContentDescription() {
    return context.getString(R.string.Slide_video);
  }
}
