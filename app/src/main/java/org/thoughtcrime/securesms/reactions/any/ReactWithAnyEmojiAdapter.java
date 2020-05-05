package org.thoughtcrime.securesms.reactions.any;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;

import java.util.List;

final class ReactWithAnyEmojiAdapter extends RecyclerView.Adapter<ReactWithAnyEmojiAdapter.ViewHolder> {

  private final List<EmojiPageModel>                               models;
  private final EmojiKeyboardProvider.EmojiEventListener           emojiEventListener;
  private final EmojiPageViewGridAdapter.VariationSelectorListener variationSelectorListener;
  private final Callbacks                                          callbacks;

  ReactWithAnyEmojiAdapter(@NonNull List<EmojiPageModel> models,
                           @NonNull EmojiKeyboardProvider.EmojiEventListener emojiEventListener,
                           @NonNull EmojiPageViewGridAdapter.VariationSelectorListener variationSelectorListener,
                           @NonNull Callbacks callbacks)
  {
    this.models                    = models;
    this.emojiEventListener        = emojiEventListener;
    this.variationSelectorListener = variationSelectorListener;
    this.callbacks                 = callbacks;
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(new EmojiPageView(parent.getContext(), emojiEventListener, variationSelectorListener, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(models.get(position));
  }

  @Override
  public int getItemCount() {
    return models.size();
  }

  @Override
  public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
    callbacks.onViewHolderAttached(holder.getAdapterPosition(), holder.emojiPageView);
  }

  @Override
  public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
    recyclerView.setNestedScrollingEnabled(false);
    ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
    params.height = (int) (recyclerView.getResources().getDisplayMetrics().heightPixels * 0.80);
    recyclerView.setLayoutParams(params);
    recyclerView.setHasFixedSize(true);
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {

    private final EmojiPageView emojiPageView;

    ViewHolder(@NonNull EmojiPageView itemView) {
      super(itemView);

      emojiPageView = itemView;

      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                 ViewGroup.LayoutParams.MATCH_PARENT);

      emojiPageView.setLayoutParams(params);
    }

    void bind(@NonNull EmojiPageModel model) {
      emojiPageView.setModel(model);
    }
  }

  interface Callbacks {
    void onViewHolderAttached(int adapterPosition, EmojiPageView pageView);
  }
}
