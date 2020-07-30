package org.thoughtcrime.securesms.reactions.any;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;
import org.thoughtcrime.securesms.util.adapter.AlwaysChangedDiffUtil;

import java.util.Collections;
import java.util.List;

final class ReactWithAnyEmojiAdapter extends ListAdapter<ReactWithAnyEmojiPage, ReactWithAnyEmojiAdapter.ReactWithAnyEmojiPageViewHolder> {

  private static final int VIEW_TYPE_SINGLE = 0;
  private static final int VIEW_TYPE_DUAL   = 1;

  private final EmojiKeyboardProvider.EmojiEventListener           emojiEventListener;
  private final EmojiPageViewGridAdapter.VariationSelectorListener variationSelectorListener;
  private final Callbacks                                          callbacks;

  ReactWithAnyEmojiAdapter(@NonNull EmojiKeyboardProvider.EmojiEventListener emojiEventListener,
                           @NonNull EmojiPageViewGridAdapter.VariationSelectorListener variationSelectorListener,
                           @NonNull Callbacks callbacks)
  {
    super(new AlwaysChangedDiffUtil<>());

    this.emojiEventListener        = emojiEventListener;
    this.variationSelectorListener = variationSelectorListener;
    this.callbacks                 = callbacks;
  }

  public ReactWithAnyEmojiPage getItem(int position) {
    return super.getItem(position);
  }

  @Override
  public @NonNull ReactWithAnyEmojiPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    switch (viewType) {
      case VIEW_TYPE_SINGLE:
        return new SinglePageBlockViewHolder(createEmojiPageView(parent.getContext()));
      case VIEW_TYPE_DUAL:
        EmojiPageView    block1     = createEmojiPageView(parent.getContext());
        EmojiPageView    block2     = createEmojiPageView(parent.getContext());
        NestedScrollView scrollView = (NestedScrollView) LayoutInflater.from(parent.getContext()).inflate(R.layout.react_with_any_emoji_dual_block_item, parent, false);
        LinearLayout     container  = scrollView.findViewById(R.id.react_with_any_emoji_dual_block_item_container);

        block1.setRecyclerNestedScrollingEnabled(false);
        block2.setRecyclerNestedScrollingEnabled(false);

        container.addView(block1, 0);
        container.addView(block2);

        return new DualPageBlockViewHolder(scrollView, block1, block2);
      default:
        throw new IllegalArgumentException("Unknown viewType: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ReactWithAnyEmojiPageViewHolder holder, int position) {
    holder.bind(getItem(position));
  }

  @Override
  public void onViewAttachedToWindow(@NonNull ReactWithAnyEmojiPageViewHolder holder) {
    callbacks.onViewHolderAttached(holder.getAdapterPosition(), holder);
  }

  @Override
  public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
    recyclerView.setNestedScrollingEnabled(false);
    ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
    params.height = (int) (recyclerView.getResources().getDisplayMetrics().heightPixels * 0.80);
    recyclerView.setLayoutParams(params);
    recyclerView.setHasFixedSize(true);
  }

  @Override
  public int getItemViewType(int position) {
    return getItem(position).getPageBlocks().size() > 1 ? VIEW_TYPE_DUAL : VIEW_TYPE_SINGLE;
  }

  private EmojiPageView createEmojiPageView(@NonNull Context context) {
    return new EmojiPageView(context, emojiEventListener, variationSelectorListener, true);
  }

  static abstract class ReactWithAnyEmojiPageViewHolder extends RecyclerView.ViewHolder implements ScrollableChild {

    public ReactWithAnyEmojiPageViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    abstract void bind(@NonNull ReactWithAnyEmojiPage reactWithAnyEmojiPage);
  }

  static final class SinglePageBlockViewHolder extends ReactWithAnyEmojiPageViewHolder {

    private final EmojiPageView emojiPageView;

    public SinglePageBlockViewHolder(@NonNull View itemView) {
      super(itemView);

      emojiPageView = (EmojiPageView) itemView;

      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                 ViewGroup.LayoutParams.MATCH_PARENT);

      emojiPageView.setLayoutParams(params);
    }

    @Override
    void bind(@NonNull ReactWithAnyEmojiPage reactWithAnyEmojiPage) {
      emojiPageView.setModel(reactWithAnyEmojiPage.getPageBlocks().get(0).getPageModel());
    }

    @Override
    public void setNestedScrollingEnabled(boolean isEnabled) {
      emojiPageView.setRecyclerNestedScrollingEnabled(isEnabled);
    }
  }

  static final class DualPageBlockViewHolder extends ReactWithAnyEmojiPageViewHolder {

    private final EmojiPageView block1;
    private final EmojiPageView block2;
    private final TextView      block2Label;

    public DualPageBlockViewHolder(@NonNull View itemView,
                                   @NonNull EmojiPageView block1,
                                   @NonNull EmojiPageView block2)
    {
      super(itemView);

      this.block1      = block1;
      this.block2      = block2;
      this.block2Label = itemView.findViewById(R.id.react_with_any_emoji_dual_block_item_block_2_label);
    }

    @Override
    void bind(@NonNull ReactWithAnyEmojiPage reactWithAnyEmojiPage) {
      block1.setModel(reactWithAnyEmojiPage.getPageBlocks().get(0).getPageModel());
      block2.setModel(reactWithAnyEmojiPage.getPageBlocks().get(1).getPageModel());
      block2Label.setText(reactWithAnyEmojiPage.getPageBlocks().get(1).getLabel());
    }

    @Override
    public void setNestedScrollingEnabled(boolean isEnabled) {
      ((NestedScrollView) itemView).setNestedScrollingEnabled(isEnabled);
    }
  }

  interface Callbacks {
    void onViewHolderAttached(int adapterPosition, ScrollableChild pageView);
  }

  interface ScrollableChild {
    void setNestedScrollingEnabled(boolean isEnabled);
  }
}
