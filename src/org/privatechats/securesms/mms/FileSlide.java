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
package org.privatechats.securesms.mms;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.privatechats.securesms.R;
import org.privatechats.securesms.attachments.Attachment;
import org.privatechats.securesms.util.ResUtil;

import java.io.IOException;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;

public class FileSlide extends Slide {

  public FileSlide(Context context, Uri uri, long dataSize) {
    super(context, constructAttachmentFromUri(context, uri, ContentType.APP_UNSPECIFIED, dataSize));
  }

  public FileSlide(Context context, Attachment attachment) {
    super(context, attachment);
  }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    return null;
  }

  @Override
  public boolean hasPlaceholder() {
    return true;
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return ResUtil.getDrawableRes(theme, R.attr.conversation_icon_attach_file);
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean isRawFile() {
    return true;
  }

  @NonNull @Override public String getContentDescription() {
    return context.getString(R.string.Slide_file);
  }
}
