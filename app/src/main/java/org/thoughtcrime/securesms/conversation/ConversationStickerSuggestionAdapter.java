package org.thoughtcrime.securesms.conversation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;

import java.util.ArrayList;
import java.util.List;

public class ConversationStickerSuggestionAdapter extends RecyclerView.Adapter<ConversationStickerSuggestionAdapter.StickerSuggestionViewHolder> {

  private final RequestManager      requestManager;
  private final EventListener       eventListener;
  private final List<StickerRecord> stickers;

  public ConversationStickerSuggestionAdapter(@NonNull RequestManager requestManager, @NonNull EventListener eventListener) {
    this.requestManager = requestManager;
    this.eventListener = eventListener;
    this.stickers      = new ArrayList<>();
  }

  @Override
  public @NonNull StickerSuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new StickerSuggestionViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.sticker_suggestion_list_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull StickerSuggestionViewHolder viewHolder, int i) {
    viewHolder.bind(requestManager, eventListener, stickers.get(i));
  }

  @Override
  public void onViewRecycled(@NonNull StickerSuggestionViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return stickers.size();
  }

  public void setStickers(@NonNull List<StickerRecord> stickers) {
    this.stickers.clear();
    this.stickers.addAll(stickers);
    notifyDataSetChanged();
  }

  static class StickerSuggestionViewHolder extends RecyclerView.ViewHolder {

    private final ImageView image;

    StickerSuggestionViewHolder(@NonNull View itemView) {
      super(itemView);
      this.image = itemView.findViewById(R.id.sticker_suggestion_item_image);
    }

    void bind(@NonNull RequestManager requestManager, @NonNull EventListener eventListener, @NonNull StickerRecord sticker) {
      requestManager.load(new DecryptableUri(sticker.uri))
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .fitCenter()
                   .into(image);

      itemView.setOnClickListener(v -> {
        eventListener.onStickerSuggestionClicked(sticker);
      });
    }

    void recycle() {
      itemView.setOnClickListener(null);
    }
  }

  public interface EventListener {
    void onStickerSuggestionClicked(@NonNull StickerRecord sticker);
  }
}
