package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.VariationSelectorListener;

public class EmojiPageView extends FrameLayout implements VariationSelectorListener {
  private static final String TAG = EmojiPageView.class.getSimpleName();

  private EmojiPageModel                   model;
  private EmojiPageViewGridAdapter         adapter;
  private RecyclerView                     recyclerView;
  private GridLayoutManager                layoutManager;
  private RecyclerView.OnItemTouchListener scrollDisabler;
  private VariationSelectorListener        variationSelectorListener;
  private EmojiVariationSelectorPopup      popup;

  public EmojiPageView(@NonNull Context context,
                       @NonNull EmojiEventListener emojiSelectionListener,
                       @NonNull VariationSelectorListener variationSelectorListener)
  {
    super(context);
    final View view = LayoutInflater.from(getContext()).inflate(R.layout.emoji_grid_layout, this, true);

    this.variationSelectorListener = variationSelectorListener;

    recyclerView   = view.findViewById(R.id.emoji);
    layoutManager  = new GridLayoutManager(context, 8);
    scrollDisabler = new ScrollDisabler();
    popup          = new EmojiVariationSelectorPopup(context, emojiSelectionListener);
    adapter        = new EmojiPageViewGridAdapter(EmojiProvider.getInstance(context),
                                                  popup,
                                                  emojiSelectionListener,
                                                  this);

    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(adapter);
  }

  public void onSelected() {
    if (model.isDynamic() && adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  public void setModel(EmojiPageModel model) {
    this.model = model;
    adapter.setEmoji(model.getDisplayEmoji());
  }

  @Override
  protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
    if (visibility != VISIBLE) {
      popup.dismiss();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    int idealWidth = getContext().getResources().getDimensionPixelOffset(R.dimen.emoji_drawer_item_width);
    layoutManager.setSpanCount(Math.max(w / idealWidth, 1));
  }

  @Override
  public void onVariationSelectorStateChanged(boolean open) {
    if (open) {
      recyclerView.addOnItemTouchListener(scrollDisabler);
    } else {
      post(() -> recyclerView.removeOnItemTouchListener(scrollDisabler));
    }

    if (variationSelectorListener != null) {
      variationSelectorListener.onVariationSelectorStateChanged(open);
    }
  }

  private static class ScrollDisabler implements RecyclerView.OnItemTouchListener {
    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent motionEvent) {
      return true;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent motionEvent) { }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean b) { }
  }
}
