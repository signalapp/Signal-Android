package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;

public final class StickerPackPreviewAdapter extends RecyclerView.Adapter<StickerPackPreviewAdapter.StickerViewHolder>  {

  private final GlideRequests                 glideRequests;
  private final EventListener                 eventListener;
  private final List<StickerManifest.Sticker> list;

  public StickerPackPreviewAdapter(@NonNull GlideRequests glideRequests, @NonNull EventListener eventListener) {
    this.glideRequests = glideRequests;
    this.eventListener = eventListener;
    this.list          = new ArrayList<>();
  }

  @Override
  public @NonNull StickerViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new StickerViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.sticker_preview_list_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull StickerViewHolder stickerViewHolder, int i) {
    stickerViewHolder.bind(glideRequests, list.get(i), eventListener);
  }

  @Override
  public int getItemCount() {
    return list.size();
  }

  @Override
  public void onViewRecycled(@NonNull StickerViewHolder holder) {
    holder.recycle();
  }

  void setStickers(List<StickerManifest.Sticker> stickers) {
    list.clear();
    list.addAll(stickers);
    notifyDataSetChanged();
  }

  static class StickerViewHolder extends RecyclerView.ViewHolder {

    private final ImageView image;

    private Object currentGlideModel;
    private String currentEmoji;

    private StickerViewHolder(@NonNull View itemView) {
      super(itemView);
      this.image = itemView.findViewById(R.id.sticker_install_item_image);
    }

    void bind(@NonNull GlideRequests glideRequests, @NonNull StickerManifest.Sticker sticker, @NonNull EventListener eventListener) {
      currentEmoji      = sticker.getEmoji();
      currentGlideModel = sticker.getUri().isPresent() ? new DecryptableStreamUriLoader.DecryptableUri(sticker.getUri().get())
                                                       : new StickerRemoteUri(sticker.getPackId(), sticker.getPackKey(), sticker.getId());
      glideRequests.load(currentGlideModel)
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .into(image);

      image.setOnLongClickListener(v -> {
        eventListener.onStickerLongPress(v);
        return true;
      });
    }

    void recycle() {
      image.setOnLongClickListener(null);
    }

    @Nullable Object getCurrentGlideModel() {
      return currentGlideModel;
    }

    @Nullable String getCurrentEmoji() {
      return currentEmoji;
    }
  }

  interface EventListener {
    void onStickerLongPress(@NonNull View view);
  }
}
