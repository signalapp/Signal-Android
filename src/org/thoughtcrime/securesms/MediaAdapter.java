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
package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.codewaves.stickyheadergrid.StickyHeaderGridAdapter;

import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.database.loaders.BucketedThreadMediaLoader.BucketedThreadMedia;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.Locale;

public class MediaAdapter extends StickyHeaderGridAdapter {
  private static final String TAG = MediaAdapter.class.getSimpleName();

  private final Context             context;
  private final MasterSecret        masterSecret;
  private final Locale              locale;
  private final Address             address;

  private  BucketedThreadMedia media;

  private static class ViewHolder extends StickyHeaderGridAdapter.ItemViewHolder {
    ThumbnailView imageView;

    ViewHolder(View v) {
      super(v);
      imageView = (ThumbnailView) v.findViewById(R.id.image);
    }
  }

  private static class HeaderHolder extends StickyHeaderGridAdapter.HeaderViewHolder {
    TextView textView;

    HeaderHolder(View itemView) {
      super(itemView);
      textView = (TextView) itemView.findViewById(R.id.text);
    }
  }

  public MediaAdapter(Context context, MasterSecret masterSecret, BucketedThreadMedia media, Locale locale, Address address) {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.locale       = locale;
    this.media        = media;
    this.address      = address;
  }

  public void setMedia(BucketedThreadMedia media) {
    this.media = media;
  }

  @Override
  public StickyHeaderGridAdapter.HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int headerType) {
    return new HeaderHolder(LayoutInflater.from(context).inflate(R.layout.media_overview_item_header, parent, false));
  }

  @Override
  public ItemViewHolder onCreateItemViewHolder(ViewGroup parent, int itemType) {
    return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.media_overview_item, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(StickyHeaderGridAdapter.HeaderViewHolder viewHolder, int section) {
    ((HeaderHolder)viewHolder).textView.setText(media.getName(section, locale));
  }

  @Override
  public void onBindItemViewHolder(ItemViewHolder viewHolder, int section, int offset) {
    MediaRecord   mediaRecord   = media.get(section, offset);
    ThumbnailView thumbnailView = ((ViewHolder)viewHolder).imageView;

    Slide slide = MediaUtil.getSlideForAttachment(context, mediaRecord.getAttachment());

    if (slide != null) {
      thumbnailView.setImageResource(masterSecret, slide, false, false);
    }

    thumbnailView.setOnClickListener(new OnMediaClickListener(mediaRecord));
  }

  @Override
  public int getSectionCount() {
    return media.getSectionCount();
  }

  @Override
  public int getSectionItemCount(int section) {
    return media.getSectionItemCount(section);
  }

  private class OnMediaClickListener implements View.OnClickListener {

    private final MediaRecord mediaRecord;

    private OnMediaClickListener(MediaRecord mediaRecord) {
      this.mediaRecord = mediaRecord;
    }

    @Override
    public void onClick(View v) {
      if (mediaRecord.getAttachment().getDataUri() != null) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, mediaRecord.getDate());
        intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, mediaRecord.getAttachment().getSize());
        intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, address);

        if (mediaRecord.getAddress() != null) {
          intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, mediaRecord.getAddress());
        }

        intent.setDataAndType(mediaRecord.getAttachment().getDataUri(), mediaRecord.getContentType());
        context.startActivity(intent);
      }
    }
  }

}
