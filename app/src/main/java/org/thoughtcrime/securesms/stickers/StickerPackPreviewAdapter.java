package org.thoughtcrime.securesms.stickers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.glide.cache.ApngOptions;
import org.thoughtcrime.securesms.mms.DecryptableUri;

import java.util.ArrayList;
import java.util.List;

public final class StickerPackPreviewAdapter extends RecyclerView.Adapter<StickerPackPreviewAdapter.StickerViewHolder>  {

  private final RequestManager                requestManager;
  private final EventListener                 eventListener;
  private final List<StickerManifest.Sticker> list;
  private final boolean                       allowApngAnimation;

  public StickerPackPreviewAdapter(@NonNull RequestManager requestManager, @NonNull EventListener eventListener, boolean allowApngAnimation) {
    this.requestManager     = requestManager;
    this.eventListener      = eventListener;
    this.allowApngAnimation = allowApngAnimation;
    this.list               = new ArrayList<>();
  }

  @Override
  public @NonNull StickerViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new StickerViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.sticker_preview_list_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull StickerViewHolder stickerViewHolder, int i) {
    stickerViewHolder.bind(requestManager, list.get(i), eventListener, allowApngAnimation);
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

    void bind(@NonNull RequestManager requestManager,
              @NonNull StickerManifest.Sticker sticker,
              @NonNull EventListener eventListener,
              boolean allowApngAnimation)
    {
      currentEmoji      = sticker.getEmoji();
      currentGlideModel = sticker.getUri().isPresent() ? new DecryptableUri(sticker.getUri().get())
                                                       : new StickerRemoteUri(sticker.getPackId(), sticker.getPackKey(), sticker.getId());
      requestManager.load(currentGlideModel)
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .set(ApngOptions.ANIMATE, allowApngAnimation)
                   .centerInside()
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
