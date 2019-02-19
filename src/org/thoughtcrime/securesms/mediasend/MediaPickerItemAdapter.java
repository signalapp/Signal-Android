package org.thoughtcrime.securesms.mediasend;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.StableIdGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MediaPickerItemAdapter extends RecyclerView.Adapter<MediaPickerItemAdapter.ItemViewHolder> {

  private final GlideRequests            glideRequests;
  private final EventListener            eventListener;
  private final List<Media>              media;
  private final Set<Media>               selected;
  private final int                      maxSelection;
  private final StableIdGenerator<Media> stableIdGenerator;

  private boolean forcedMultiSelect;

  public MediaPickerItemAdapter(@NonNull GlideRequests glideRequests, @NonNull EventListener eventListener, int maxSelection) {
    this.glideRequests     = glideRequests;
    this.eventListener     = eventListener;
    this.media             = new ArrayList<>();
    this.maxSelection      = maxSelection;
    this.stableIdGenerator = new StableIdGenerator<>();
    this.selected          = new TreeSet<>((m1, m2) -> {
      if      (m1.equals(m2))                                 return 0;
      else if (Long.compare(m2.getDate(), m1.getDate()) == 0) return m2.getUri().compareTo(m1.getUri());
      else                                                    return Long.compare(m2.getDate(), m1.getDate());
    });

    setHasStableIds(true);
  }

  @Override
  public @NonNull ItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new ItemViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.mediapicker_media_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ItemViewHolder holder, int i) {
    holder.bind(media.get(i), forcedMultiSelect, selected, maxSelection, glideRequests, eventListener);
  }

  @Override
  public void onViewRecycled(@NonNull ItemViewHolder holder) {
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

  void setMedia(@NonNull List<Media> media) {
    this.media.clear();
    this.media.addAll(media);
    notifyDataSetChanged();
  }

  void setSelected(@NonNull Collection<Media> selected) {
    this.selected.clear();
    this.selected.addAll(selected);
    notifyDataSetChanged();
  }

  Set<Media> getSelected() {
    return selected;
  }

  void setForcedMultiSelect(boolean forcedMultiSelect) {
    this.forcedMultiSelect = forcedMultiSelect;
    notifyDataSetChanged();
  }

  static class ItemViewHolder extends RecyclerView.ViewHolder {

    private final ImageView thumbnail;
    private final View      playOverlay;
    private final View      selectedOverlay;

    ItemViewHolder(@NonNull View itemView) {
      super(itemView);
      thumbnail       = itemView.findViewById(R.id.mediapicker_image_item_thumbnail);
      playOverlay     = itemView.findViewById(R.id.mediapicker_play_overlay);
      selectedOverlay = itemView.findViewById(R.id.mediapicker_selected);
    }

    void bind(@NonNull Media media, boolean multiSelect, Set<Media> selected, int maxSelection, @NonNull GlideRequests glideRequests, @NonNull EventListener eventListener) {
      glideRequests.load(media.getUri())
                   .diskCacheStrategy(DiskCacheStrategy.NONE)
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .into(thumbnail);

      playOverlay.setVisibility(MediaUtil.isVideoType(media.getMimeType()) ? View.VISIBLE : View.GONE);
      selectedOverlay.setVisibility(selected.contains(media) ? View.VISIBLE : View.GONE);

      if (selected.isEmpty() && !multiSelect) {
        itemView.setOnClickListener(v -> eventListener.onMediaChosen(media));
        if (maxSelection > 1) {
          itemView.setOnLongClickListener(v -> {
            selected.add(media);
            eventListener.onMediaSelectionChanged(new ArrayList<>(selected));
            return true;
          });
        }
      } else if (selected.contains(media)) {
        itemView.setOnClickListener(v -> {
          selected.remove(media);
          eventListener.onMediaSelectionChanged(new ArrayList<>(selected));
        });
      } else {
        itemView.setOnClickListener(v -> {
          if (selected.size() < maxSelection) {
            selected.add(media);
            eventListener.onMediaSelectionChanged(new ArrayList<>(selected));
          } else {
            eventListener.onMediaSelectionOverflow(maxSelection);
          }
        });
      }
    }

    void recycle() {
      itemView.setOnClickListener(null);
    }
  }

  interface EventListener {
    void onMediaChosen(@NonNull Media media);
    void onMediaSelectionChanged(@NonNull List<Media> media);
    void onMediaSelectionOverflow(int maxSelection);
  }
}
