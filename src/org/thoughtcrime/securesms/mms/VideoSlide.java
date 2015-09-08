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

import java.io.IOException;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.SmilUtil;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILRegionElement;

import de.gdata.messaging.util.VideoResolutionChanger;
import ws.com.google.android.mms.pdu.PduPart;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.DrawableRes;
import android.util.Log;

public class VideoSlide extends Slide {
  private static final String TAG = VideoSlide.class.getSimpleName();

  public VideoSlide(Context context, Uri uri, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri, sendOrReceive));
  }
  public VideoSlide(Context context, Uri uri, String contentType, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri, contentType, sendOrReceive));
  }
  public VideoSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }
  @Override
  public @DrawableRes
  int getPlaceholderRes(Resources.Theme theme) {
    return R.drawable.ic_launcher_video_player;
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasVideo() {
    return true;
  }

  @Override
  public SMILRegionElement getSmilRegion(SMILDocument document) {
    SMILRegionElement region = (SMILRegionElement) document.createElement("region");
    region.setId("Image");
    region.setLeft(0);
    region.setTop(0);
    region.setWidth(SmilUtil.ROOT_WIDTH);
    region.setHeight(SmilUtil.ROOT_HEIGHT);
    region.setFit("meet");
    return region;
  }

  @Override
  public SMILMediaElement getMediaElement(SMILDocument document) {
    return SmilUtil.createMediaElement("video", document, new String(getPart().getName()));
  }

  private static PduPart constructPartFromUri(Context context, Uri uri, String contentType, boolean sendOrReceive)
      throws IOException, MediaTooLargeException
  {
    PduPart part  = new PduPart();

    assertMediaSize(context, uri, sendOrReceive);
    Log.w(TAG, "Setting mime type: " + contentType);
    part.setContentType(contentType.getBytes());

    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Video" + System.currentTimeMillis()).getBytes());

    return part;
  }
  @Override
  public Uri getThumbnailUri() {
    if (!getPart().isPendingPush() && getPart().getDataUri() != null) {
      return isDraft()
          ? getPart().getDataUri()
          : PartAuthority.getThumbnailUri(getPart().getPartId());
    }

    return null;
  }
  private static PduPart constructPartFromUri(Context context, Uri uri, boolean sendOrReceive)
      throws IOException, MediaTooLargeException
  {
    PduPart         part     = new PduPart();
    ContentResolver resolver = context.getContentResolver();
    Cursor cursor   = null;

    try {
      cursor = resolver.query(uri, new String[] {MediaStore.Video.Media.MIME_TYPE}, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
        part.setContentType(cursor.getString(0).getBytes());
      } else {
        part.setContentType(VideoResolutionChanger.OUTPUT_VIDEO_MIME_TYPE.getBytes());
      }
      Log.w("VideoSlide", "Setting mime type: " +part.getContentType());
    } finally {
      if (cursor != null)
        cursor.close();
    }

    assertMediaSize(context, uri, sendOrReceive);
    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Video" + System.currentTimeMillis()).getBytes());

    return part;
  }
}
