package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for a specific page in the sticker keyboard. Shows the stickers in a grid.
 * @see StickerKeyboardPageFragment
 */
final class StickerKeyboardPageAdapter extends RecyclerView.Adapter<StickerKeyboardPageAdapter.StickerKeyboardPageViewHolder> {

  private final GlideRequests       glideRequests;
  private final EventListener       eventListener;
  private final List<StickerRecord> stickers;

  private int stickerSize;

  StickerKeyboardPageAdapter(@NonNull GlideRequests glideRequests, @NonNull EventListener eventListener) {
    this.glideRequests = glideRequests;
    this.eventListener = eventListener;
    this.stickers      = new ArrayList<>();

    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    return stickers.get(position).getRowId();
  }

  @Override
  public @NonNull
  StickerKeyboardPageViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new StickerKeyboardPageViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.sticker_keyboard_page_list_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull StickerKeyboardPageViewHolder viewHolder, int i) {
    viewHolder.bind(glideRequests, eventListener, stickers.get(i), stickerSize);
  }

  @Override
  public void onViewRecycled(@NonNull StickerKeyboardPageViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return stickers.size();
  }

  void setStickers(@NonNull List<StickerRecord> stickers, @Px int stickerSize) {
    this.stickers.clear();
    this.stickers.addAll(stickers);

    this.stickerSize = stickerSize;

    notifyDataSetChanged();
  }

  void setStickerSize(@Px int stickerSize) {
    this.stickerSize = stickerSize;
    notifyDataSetChanged();
  }

  static class StickerKeyboardPageViewHolder extends RecyclerView.ViewHolder {

    private final ImageView image;

    private StickerRecord currentSticker;

    public StickerKeyboardPageViewHolder(@NonNull View itemView) {
      super(itemView);
      image = itemView.findViewById(R.id.sticker_keyboard_page_image);
    }

    public void bind(@NonNull GlideRequests glideRequests,
                     @Nullable EventListener eventListener,
                     @NonNull StickerRecord sticker,
                     @Px int size)
    {
      currentSticker = sticker;

      itemView.getLayoutParams().height = size;
      itemView.getLayoutParams().width  = size;
      itemView.requestLayout();

      glideRequests.load(new DecryptableUri(sticker.getUri()))
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .into(image);

      if (eventListener != null) {
        image.setOnClickListener(v -> eventListener.onStickerClicked(sticker));
        image.setOnLongClickListener(v -> {
          eventListener.onStickerLongClicked(v);
          return true;
        });
      } else {
        image.setOnClickListener(null);
        image.setOnLongClickListener(null);
      }
    }

    void recycle() {
      image.setOnClickListener(null);
    }

    @Nullable StickerRecord getCurrentSticker() {
      return currentSticker;
    }
  }

  interface EventListener {
    void onStickerClicked(@NonNull StickerRecord sticker);
    void onStickerLongClicked(@NonNull View targetView);
  }
}
