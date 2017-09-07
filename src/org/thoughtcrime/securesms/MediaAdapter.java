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
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.MediaAdapter.ViewHolder;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MediaAdapter extends CursorRecyclerViewAdapter<ViewHolder> {
  private static final String TAG = MediaAdapter.class.getSimpleName();

  private final Set<MediaRecord> batchSelected = Collections.synchronizedSet(new HashSet<MediaRecord>());

  private final MasterSecret      masterSecret;
  private final ItemClickListener clickListener;
  private final long              threadId;

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public ThumbnailView imageView;

    public ViewHolder(View v) {
      super(v);
      imageView = (ThumbnailView) v.findViewById(R.id.image);
    }
  }

  public interface ItemClickListener {
    void onItemClick(MediaRecord item);
    void onItemLongClick(MediaRecord item);
  }

  public MediaAdapter(Context context, MasterSecret masterSecret, ItemClickListener clickListener,
                      Cursor c, long threadId)
  {
    super(context, c);
    this.masterSecret  = masterSecret;
    this.clickListener = clickListener;
    this.threadId      = threadId;
  }

  @Override
  public ViewHolder onCreateItemViewHolder(final ViewGroup viewGroup, final int i) {
    final View view = LayoutInflater.from(getContext()).inflate(R.layout.media_overview_item, viewGroup, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindItemViewHolder(final ViewHolder viewHolder, final @NonNull Cursor cursor) {
    final ThumbnailView        imageView   = viewHolder.imageView;
    final MediaRecord          mediaRecord = MediaRecord.from(getContext(), masterSecret, cursor);
    final OnMediaClickListener listener    = new OnMediaClickListener(mediaRecord);

    Slide slide = MediaUtil.getSlideForAttachment(getContext(), mediaRecord.getAttachment());

    if (slide != null) {
      imageView.setImageResource(masterSecret, slide, false, false);
    }

    imageView.setSelected(batchSelected.contains(mediaRecord));

    imageView.setOnClickListener(listener);
    imageView.setOnLongClickListener(listener);
  }

  public void toggleSelection(MediaRecord mediaRecord) {
    if (!batchSelected.remove(mediaRecord)) {
      batchSelected.add(mediaRecord);
    }
    this.notifyDataSetChanged();
  }

  public void selectAllMedia() {
    for (int i = 0; i < getItemCount(); i++) {
      batchSelected.add(MediaRecord.from(getContext(), masterSecret, getCursorAtPositionOrThrow(i)));
    }
    this.notifyDataSetChanged();
  }

  public void clearSelection() {
    batchSelected.clear();
    this.notifyDataSetChanged();
  }

  public Set<MediaRecord> getSelectedItems() {
    return Collections.unmodifiableSet(new HashSet<>(batchSelected));
  }

  private class OnMediaClickListener implements OnClickListener, OnLongClickListener {
    private final MediaRecord mediaRecord;

    private OnMediaClickListener(MediaRecord mediaRecord) {
      this.mediaRecord = mediaRecord;
    }

    @Override
    public void onClick(View v) {
      if (!batchSelected.isEmpty()) {
        clickListener.onItemClick(mediaRecord);
      } else if (mediaRecord.getAttachment().getDataUri() != null) {
        Intent intent = new Intent(getContext(), MediaPreviewActivity.class);
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, mediaRecord.getDate());
        intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, mediaRecord.getAttachment().getSize());
        intent.putExtra(MediaPreviewActivity.THREAD_ID_EXTRA, threadId);

        if (mediaRecord.getAddress() != null) {
          intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, mediaRecord.getAddress());
        }

        intent.setDataAndType(mediaRecord.getAttachment().getDataUri(), mediaRecord.getContentType());
        getContext().startActivity(intent);
      }
    }

    @Override
    public boolean onLongClick(View v) {
      clickListener.onItemLongClick(mediaRecord);
      return true;
    }
  }
}
