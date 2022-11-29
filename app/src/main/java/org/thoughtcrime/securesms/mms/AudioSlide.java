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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.DraftTable;
import org.thoughtcrime.securesms.util.MediaUtil;


public class AudioSlide extends Slide {

  public static @NonNull AudioSlide createFromVoiceNoteDraft(@NonNull Context context, @NonNull DraftTable.Draft draft) {
    VoiceNoteDraft voiceNoteDraft = VoiceNoteDraft.fromDraft(draft);

    return new AudioSlide(context, new UriAttachment(voiceNoteDraft.getUri(),
                                                     MediaUtil.AUDIO_AAC,
                                                     AttachmentTable.TRANSFER_PROGRESS_DONE,
                                                     voiceNoteDraft.getSize(),
                                                     0,
                                                     0,
                                                     null,
                                                     null,
                                                     true,
                                                     false,
                                                     false,
                                                     false,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null));
  }

  public AudioSlide(Context context, Uri uri, long dataSize, boolean voiceNote) {
    super(context, constructAttachmentFromUri(context, uri, MediaUtil.AUDIO_UNSPECIFIED, dataSize, 0, 0, false, null, null, null, null, null, voiceNote, false, false, false));
  }

  public AudioSlide(Context context, Uri uri, long dataSize, String contentType, boolean voiceNote) {
    super(context,  new UriAttachment(uri, contentType, AttachmentTable.TRANSFER_PROGRESS_STARTED, dataSize, 0, 0, null, null, voiceNote, false, false, false, null, null, null, null, null));
  }

  public AudioSlide(Context context, Attachment attachment) {
    super(context, attachment);
  }

  @Override
  public boolean hasPlaceholder() {
    return true;
  }

  @Override
  public boolean hasImage() {
    return false;
  }

  @Override
  public boolean hasAudio() {
    return true;
  }

  @NonNull
  @Override
  public String getContentDescription() {
    return context.getString(R.string.Slide_audio);
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return R.drawable.ic_audio;
  }
}
