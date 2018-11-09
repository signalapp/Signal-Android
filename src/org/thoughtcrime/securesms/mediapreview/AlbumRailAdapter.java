package org.thoughtcrime.securesms.mediapreview;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;

public class AlbumRailAdapter extends RecyclerView.Adapter<AlbumRailAdapter.AlbumRailViewHolder> {

  private final GlideRequests           glideRequests;
  private final List<MediaRecord>       records;
  private final RailItemClickedListener listener;

  private int activePosition;

  public AlbumRailAdapter(@NonNull GlideRequests glideRequests, @NonNull RailItemClickedListener listener) {
    this.glideRequests = glideRequests;
    this.records       = new ArrayList<>();
    this.listener      = listener;

    setHasStableIds(true);
  }

  @NonNull
  @Override
  public AlbumRailViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new AlbumRailViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.media_preview_album_rail_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull AlbumRailViewHolder albumRailViewHolder, int i) {
    albumRailViewHolder.bind(records.get(i), i == activePosition, glideRequests, listener, i - activePosition);
  }

  @Override
  public void onViewRecycled(@NonNull AlbumRailViewHolder holder) {
    holder.recycle();
  }

  @Override
  public long getItemId(int position) {
    return records.get(position).getAttachment().getAttachmentId().getUniqueId();
  }

  @Override
  public int getItemCount() {
    return records.size();
  }

  public void setRecords(@NonNull List<MediaRecord> records, int activePosition) {
    this.activePosition = activePosition;

    this.records.clear();
    this.records.addAll(records);

    notifyDataSetChanged();
  }

  static class AlbumRailViewHolder extends RecyclerView.ViewHolder {

    private final ThumbnailView image;

    AlbumRailViewHolder(@NonNull View itemView) {
      super(itemView);
      image = (ThumbnailView) itemView;
    }

    void bind(@NonNull MediaRecord record, boolean isActive, @NonNull GlideRequests glideRequests,
              @NonNull RailItemClickedListener railItemClickedListener, int distanceFromActive)
    {
      if (record.getAttachment().getThumbnailUri() != null) {
        image.setImageResource(glideRequests, record.getAttachment().getThumbnailUri());
      } else if (record.getAttachment().getDataUri() != null) {
        image.setImageResource(glideRequests, record.getAttachment().getDataUri());
      } else {
        image.clear(glideRequests);
      }

      image.setBackgroundResource(isActive ? R.drawable.album_rail_item_background : 0);
      image.setOnClickListener(v -> railItemClickedListener.onRailItemClicked(distanceFromActive));
    }

    void recycle() {
      image.setOnClickListener(null);
    }
  }

  public interface RailItemClickedListener {
    void onRailItemClicked(int distanceFromActive);
  }
}
