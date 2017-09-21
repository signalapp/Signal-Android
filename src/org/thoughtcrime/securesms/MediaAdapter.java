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
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.MediaAdapter.ViewHolder;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.MediaUtil;

public class MediaAdapter extends CursorRecyclerViewAdapter<ViewHolder> {
  private static final String TAG = MediaAdapter.class.getSimpleName();

  private final MasterSecret masterSecret;
  private final Address      address;

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public ThumbnailView imageView;

    public ViewHolder(View v) {
      super(v);
      imageView = (ThumbnailView) v.findViewById(R.id.image);
    }
  }

  public MediaAdapter(Context context, MasterSecret masterSecret, Cursor c, Address address) {
    super(context, c);
    this.masterSecret = masterSecret;
    this.address      = address;
  }

  @Override
  public ViewHolder onCreateItemViewHolder(final ViewGroup viewGroup, final int i) {
    final View view = LayoutInflater.from(getContext()).inflate(R.layout.media_overview_item, viewGroup, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindItemViewHolder(final ViewHolder viewHolder, final @NonNull Cursor cursor) {
    final ThumbnailView imageView   = viewHolder.imageView;
    final MediaRecord   mediaRecord = MediaRecord.from(getContext(), masterSecret, cursor);

    Slide slide = MediaUtil.getSlideForAttachment(getContext(), mediaRecord.getAttachment());

    if (slide != null) {
      imageView.setImageResource(masterSecret, slide, false, false);
    }

    imageView.setOnClickListener(new OnMediaClickListener(mediaRecord));
  }

  private class OnMediaClickListener implements OnClickListener {
    private final MediaRecord mediaRecord;

    private OnMediaClickListener(MediaRecord mediaRecord) {
      this.mediaRecord = mediaRecord;
    }

    @Override
    public void onClick(View v) {
      if (mediaRecord.getAttachment().getDataUri() != null) {
        Intent intent = new Intent(getContext(), MediaPreviewActivity.class);
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, mediaRecord.getDate());
        intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, mediaRecord.getAttachment().getSize());
        intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, address);

        if (mediaRecord.getAddress() != null) {
          intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, mediaRecord.getAddress());
        }

        intent.setDataAndType(mediaRecord.getAttachment().getDataUri(), mediaRecord.getContentType());
        getContext().startActivity(intent);
      }
    }
  }
}
