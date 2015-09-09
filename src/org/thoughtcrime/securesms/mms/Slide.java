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
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;

import ws.com.google.android.mms.pdu.PduPart;

public abstract class Slide {

  protected final PduPart part;
  protected final Context context;

  public Slide(Context context, @NonNull PduPart part) {
    this.part    = part;
    this.context = context;
  }

  public String getContentType() {
    return new String(part.getContentType());
  }

  public Uri getUri() {
    return part.getDataUri();
  }

  public boolean hasImage() {
    return false;
  }

  public boolean hasVideo() {
    return false;
  }

  public boolean hasAudio() {
    return false;
  }

  public PduPart getPart() {
    return part;
  }

  public Uri getThumbnailUri() {
    return null;
  }

  public boolean isInProgress() {
    return part.isInProgress();
  }

  public boolean isPendingDownload() {
    return getTransferProgress() == PartDatabase.TRANSFER_PROGRESS_FAILED ||
           getTransferProgress() == PartDatabase.TRANSFER_PROGRESS_AUTO_PENDING;
  }

  public long getTransferProgress() {
    return part.getTransferProgress();
  }

  public @DrawableRes int getPlaceholderRes(Theme theme) {
    throw new AssertionError("getPlaceholderRes() called for non-drawable slide");
  }

  public boolean isDraft() {
    return !getPart().getPartId().isValid();
  }


  protected static PduPart constructPartFromUri(@NonNull  Context      context,
                                                @NonNull  Uri          uri,
                                                @NonNull  String       defaultMime,
                                                          long         dataSize)
      throws IOException
  {
    final PduPart part            = new PduPart();
    final String  mimeType        = MediaUtil.getMimeType(context, uri);
    final String  derivedMimeType = mimeType != null ? mimeType : defaultMime;

    part.setDataSize(dataSize);
    part.setDataUri(uri);
    part.setContentType(derivedMimeType.getBytes());
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName((MediaUtil.getDiscreteMimeType(derivedMimeType) + System.currentTimeMillis()).getBytes());

    return part;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Slide)) return false;

    Slide that = (Slide)other;

    return Util.equals(this.getContentType(), that.getContentType()) &&
           this.hasAudio() == that.hasAudio()                        &&
           this.hasImage() == that.hasImage()                        &&
           this.hasVideo() == that.hasVideo()                        &&
           this.isDraft() == that.isDraft()                          &&
           this.getTransferProgress() == that.getTransferProgress()  &&
           Util.equals(this.getUri(), that.getUri())                 &&
           Util.equals(this.getThumbnailUri(), that.getThumbnailUri());
  }

  @Override
  public int hashCode() {
    return Util.hashCode(getContentType(), hasAudio(), hasImage(),
                         hasVideo(), isDraft(), getUri(), getThumbnailUri(), getTransferProgress());
  }
}
