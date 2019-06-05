package org.thoughtcrime.securesms.mediasend;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.StableIdGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class MediaPickerItemAdapter extends RecyclerView.Adapter<MediaPickerItemAdapter.ItemViewHolder> {

  private final GlideRequests            glideRequests;
  private final EventListener            eventListener;
  private final List<Media>              media;
  private final List<Media>              selected;
  private final int                      maxSelection;
  private final StableIdGenerator<Media> stableIdGenerator;

  private boolean forcedMultiSelect;

  public MediaPickerItemAdapter(@NonNull GlideRequests glideRequests, @NonNull EventListener eventListener, int maxSelection) {
    this.glideRequests     = glideRequests;
    this.eventListener     = eventListener;
    this.media             = new ArrayList<>();
    this.maxSelection      = maxSelection;
    this.stableIdGenerator = new StableIdGenerator<>();
    this.selected          = new LinkedList<>();

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

  List<Media> getSelected() {
    return selected;
  }

  void setForcedMultiSelect(boolean forcedMultiSelect) {
    this.forcedMultiSelect = forcedMultiSelect;
    notifyDataSetChanged();
  }

  static class ItemViewHolder extends RecyclerView.ViewHolder {

    private final ImageView thumbnail;
    private final View      playOverlay;
    private final View      selectOn;
    private final View      selectOff;
    private final View      selectOverlay;
    private final TextView  selectOrder;

    ItemViewHolder(@NonNull View itemView) {
      super(itemView);
      thumbnail     = itemView.findViewById(R.id.mediapicker_image_item_thumbnail);
      playOverlay   = itemView.findViewById(R.id.mediapicker_play_overlay);
      selectOn      = itemView.findViewById(R.id.mediapicker_select_on);
      selectOff     = itemView.findViewById(R.id.mediapicker_select_off);
      selectOverlay = itemView.findViewById(R.id.mediapicker_select_overlay);
      selectOrder   = itemView.findViewById(R.id.mediapicker_select_order);
    }

    void bind(@NonNull Media media, boolean multiSelect, List<Media> selected, int maxSelection, @NonNull GlideRequests glideRequests, @NonNull EventListener eventListener) {
      glideRequests.load(media.getUri())
                   .diskCacheStrategy(DiskCacheStrategy.NONE)
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .into(thumbnail);

      playOverlay.setVisibility(MediaUtil.isVideoType(media.getMimeType()) ? View.VISIBLE : View.GONE);

      if (selected.isEmpty() && !multiSelect) {
        itemView.setOnClickListener(v -> eventListener.onMediaChosen(media));
        selectOn.setVisibility(View.GONE);
        selectOff.setVisibility(View.GONE);
        selectOverlay.setVisibility(View.GONE);

        if (maxSelection > 1) {
          itemView.setOnLongClickListener(v -> {
            selected.add(media);
            eventListener.onMediaSelectionStarted();
            eventListener.onMediaSelectionChanged(new ArrayList<>(selected));
            return true;
          });
        }
      } else if (selected.contains(media)) {
        selectOff.setVisibility(View.VISIBLE);
        selectOn.setVisibility(View.VISIBLE);
        selectOverlay.setVisibility(View.VISIBLE);
        selectOrder.setText(String.valueOf(selected.indexOf(media) + 1));
        itemView.setOnLongClickListener(null);
        itemView.setOnClickListener(v -> {
          selected.remove(media);
          eventListener.onMediaSelectionChanged(new ArrayList<>(selected));
        });
      } else {
        selectOff.setVisibility(View.VISIBLE);
        selectOn.setVisibility(View.GONE);
        selectOverlay.setVisibility(View.GONE);
        itemView.setOnLongClickListener(null);
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
    void onMediaSelectionStarted();
    void onMediaSelectionChanged(@NonNull List<Media> media);
    void onMediaSelectionOverflow(int maxSelection);
  }
}
