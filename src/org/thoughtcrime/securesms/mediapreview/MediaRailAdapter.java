package org.thoughtcrime.securesms.mediapreview;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.StableIdGenerator;

import java.util.ArrayList;
import java.util.List;

public class MediaRailAdapter extends RecyclerView.Adapter<MediaRailAdapter.MediaRailViewHolder> {

  private final GlideRequests            glideRequests;
  private final List<Media>              media;
  private final RailItemListener         listener;
  private final boolean                  deleteEnabled;
  private final StableIdGenerator<Media> stableIdGenerator;

  private int activePosition;

  public MediaRailAdapter(@NonNull GlideRequests glideRequests, @NonNull RailItemListener listener, boolean deleteEnabled) {
    this.glideRequests     = glideRequests;
    this.media             = new ArrayList<>();
    this.listener          = listener;
    this.deleteEnabled     = deleteEnabled;
    this.stableIdGenerator = new StableIdGenerator<>();

    setHasStableIds(true);
  }

  @NonNull
  @Override
  public MediaRailViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new MediaRailViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.media_preview_album_rail_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull MediaRailViewHolder mediaRailViewHolder, int i) {
    mediaRailViewHolder.bind(media.get(i), i == activePosition, glideRequests, listener, i - activePosition, deleteEnabled);
  }

  @Override
  public void onViewRecycled(@NonNull MediaRailViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return media.size();
  }

  @Override
  public long getItemId(int position) {
    return stableIdGenerator.getId(media.get(position));
  }

  public void setMedia(@NonNull List<Media> media) {
    setMedia(media, activePosition);
  }

  public void setMedia(@NonNull List<Media> records, int activePosition) {
    this.activePosition = activePosition;

    this.media.clear();
    this.media.addAll(records);

    notifyDataSetChanged();
  }

  public void setActivePosition(int activePosition) {
    this.activePosition = activePosition;
    notifyDataSetChanged();
  }

  static class MediaRailViewHolder extends RecyclerView.ViewHolder {

    private final ThumbnailView image;
    private final View          deleteButton;

    MediaRailViewHolder(@NonNull View itemView) {
      super(itemView);
      image        = itemView.findViewById(R.id.rail_item_image);
      deleteButton = itemView.findViewById(R.id.rail_item_delete);
    }

    void bind(@NonNull Media media, boolean isActive, @NonNull GlideRequests glideRequests,
              @NonNull RailItemListener railItemListener, int distanceFromActive, boolean deleteEnabled)
    {
      image.setImageResource(glideRequests, media.getUri());
      image.setBackgroundResource(isActive ? R.drawable.media_rail_item_background : 0);
      image.setOnClickListener(v -> railItemListener.onRailItemClicked(distanceFromActive));

      if (deleteEnabled && isActive) {
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setOnClickListener(v -> railItemListener.onRailItemDeleteClicked(distanceFromActive));
      } else {
        deleteButton.setVisibility(View.GONE);
      }
    }

    void recycle() {
      image.setOnClickListener(null);
      deleteButton.setOnClickListener(null);
    }
  }

  public interface RailItemListener {
    void onRailItemClicked(int distanceFromActive);
    void onRailItemDeleteClicked(int distanceFromActive);
  }
}
