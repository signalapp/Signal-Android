package org.thoughtcrime.securesms.mediapreview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
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

  private static final int TYPE_MEDIA  = 1;
  private static final int TYPE_BUTTON = 2;

  private final GlideRequests            glideRequests;
  private final List<Media>              media;
  private final RailItemListener         listener;
  private final boolean                  editable;
  private final StableIdGenerator<Media> stableIdGenerator;

  private RailItemAddListener addListener;
  private int activePosition;

  public MediaRailAdapter(@NonNull GlideRequests glideRequests, @NonNull RailItemListener listener, boolean editable) {
    this.glideRequests     = glideRequests;
    this.media             = new ArrayList<>();
    this.listener          = listener;
    this.editable          = editable;
    this.stableIdGenerator = new StableIdGenerator<>();

    setHasStableIds(true);
  }

  @NonNull
  @Override
  public MediaRailViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int type) {
    switch (type) {
      case TYPE_MEDIA:
        return new MediaViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.mediarail_media_item, viewGroup, false));
      case TYPE_BUTTON:
        return new ButtonViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.mediarail_button_item, viewGroup, false));
      default:
        throw new UnsupportedOperationException("Unsupported view type: " + type);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull MediaRailViewHolder viewHolder, int i) {
    switch (getItemViewType(i)) {
      case TYPE_MEDIA:
        ((MediaViewHolder) viewHolder).bind(media.get(i), i == activePosition, glideRequests, listener, i - activePosition, editable);
        break;
      case TYPE_BUTTON:
        ((ButtonViewHolder) viewHolder).bind(addListener);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported view type: " + getItemViewType(i));
    }
  }

  @Override
  public int getItemViewType(int position) {
    if (editable && position == getItemCount() - 1) {
      return TYPE_BUTTON;
    } else {
      return TYPE_MEDIA;
    }
  }

  @Override
  public void onViewRecycled(@NonNull MediaRailViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return editable ? media.size() + 1 : media.size();
  }

  @Override
  public long getItemId(int position) {
    switch (getItemViewType(position)) {
      case TYPE_MEDIA:
        return stableIdGenerator.getId(media.get(position));
      case TYPE_BUTTON:
        return Long.MAX_VALUE;
      default:
        throw new UnsupportedOperationException("Unsupported view type: " + getItemViewType(position));
    }
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

  public void setAddButtonListener(@Nullable RailItemAddListener addListener) {
    this.addListener = addListener;
    notifyDataSetChanged();
  }

  static abstract class MediaRailViewHolder extends RecyclerView.ViewHolder {
    public MediaRailViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    abstract void recycle();
  }

  static class MediaViewHolder extends MediaRailViewHolder {

    private final ThumbnailView image;
    private final View          outline;
    private final View          deleteButton;
    private final View          captionIndicator;

    MediaViewHolder(@NonNull View itemView) {
      super(itemView);
      image            = itemView.findViewById(R.id.rail_item_image);
      outline          = itemView.findViewById(R.id.rail_item_outline);
      deleteButton     = itemView.findViewById(R.id.rail_item_delete);
      captionIndicator = itemView.findViewById(R.id.rail_item_caption);
    }

    void bind(@NonNull Media media, boolean isActive, @NonNull GlideRequests glideRequests,
              @NonNull RailItemListener railItemListener, int distanceFromActive, boolean editable)
    {
      image.setImageResource(glideRequests, media.getUri());
      image.setOnClickListener(v -> railItemListener.onRailItemClicked(distanceFromActive));

      outline.setVisibility(isActive ? View.VISIBLE : View.GONE);

      captionIndicator.setVisibility(media.getCaption().isPresent() ? View.VISIBLE : View.GONE);

      if (editable && isActive) {
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

  static class ButtonViewHolder extends MediaRailViewHolder {

    public ButtonViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    void bind(@Nullable RailItemAddListener addListener) {
      if (addListener != null) {
        itemView.setOnClickListener(v -> addListener.onRailItemAddClicked());
      }
    }

    @Override
    void recycle() {
      itemView.setOnClickListener(null);
    }
  }

  public interface RailItemListener {
    void onRailItemClicked(int distanceFromActive);
    void onRailItemDeleteClicked(int distanceFromActive);
  }

  public interface RailItemAddListener {
    void onRailItemAddClicked();
  }
}
