package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.EmojiHeader;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.EmojiNoResultsModel;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.VariationSelectorListener;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.List;
import java.util.Optional;

public class EmojiPageView extends RecyclerView implements VariationSelectorListener {

  private AdapterFactory                   adapterFactory;
  private LinearLayoutManager              layoutManager;
  private RecyclerView.OnItemTouchListener scrollDisabler;
  private VariationSelectorListener        variationSelectorListener;
  private EmojiVariationSelectorPopup      popup;

  public EmojiPageView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public EmojiPageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public EmojiPageView(@NonNull Context context,
                       @NonNull EmojiEventListener emojiSelectionListener,
                       @NonNull VariationSelectorListener variationSelectorListener,
                       boolean allowVariations)
  {
    super(context);
    initialize(emojiSelectionListener, variationSelectorListener, allowVariations);
  }

  public EmojiPageView(@NonNull Context context,
                       @NonNull EmojiEventListener emojiSelectionListener,
                       @NonNull VariationSelectorListener variationSelectorListener,
                       boolean allowVariations,
                       @NonNull LinearLayoutManager layoutManager,
                       @LayoutRes int displayEmojiLayoutResId,
                       @LayoutRes int displayEmoticonLayoutResId)
  {
    super(context);
    initialize(emojiSelectionListener, variationSelectorListener, allowVariations, layoutManager, displayEmojiLayoutResId, displayEmoticonLayoutResId);
  }

  public void initialize(@NonNull EmojiEventListener emojiSelectionListener,
                         @NonNull VariationSelectorListener variationSelectorListener,
                         boolean allowVariations)
  {
    initialize(emojiSelectionListener, variationSelectorListener, allowVariations, new GridLayoutManager(getContext(), 8), R.layout.emoji_display_item_grid, R.layout.emoji_text_display_item_grid);
  }

  public void initialize(@NonNull EmojiEventListener emojiSelectionListener,
                         @NonNull VariationSelectorListener variationSelectorListener,
                         boolean allowVariations,
                         @NonNull LinearLayoutManager layoutManager,
                         @LayoutRes int displayEmojiLayoutResId,
                         @LayoutRes int displayEmoticonLayoutResId)
  {
    this.variationSelectorListener = variationSelectorListener;

    this.layoutManager  = layoutManager;
    this.scrollDisabler = new ScrollDisabler();
    this.popup          = new EmojiVariationSelectorPopup(getContext(), emojiSelectionListener);
    this.adapterFactory = () -> new EmojiPageViewGridAdapter(popup,
                                                             emojiSelectionListener,
                                                             this,
                                                             allowVariations,
                                                             displayEmojiLayoutResId,
                                                             displayEmoticonLayoutResId);

    if (this.layoutManager instanceof GridLayoutManager) {
      GridLayoutManager gridLayout = (GridLayoutManager) this.layoutManager;
      gridLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
        @Override
        public int getSpanSize(int position) {
          if (getAdapter() != null) {
            Optional<MappingModel<?>> model = getAdapter().getModel(position);
            if (model.isPresent() && (model.get() instanceof EmojiHeader || model.get() instanceof EmojiNoResultsModel)) {
              return gridLayout.getSpanCount();
            }
          }
          return 1;
        }
      });
    }

    setLayoutManager(layoutManager);

    Drawable drawable = DrawableUtil.tint(ContextUtil.requireDrawable(getContext(), R.drawable.triangle_bottom_right_corner), ContextCompat.getColor(getContext(), R.color.signal_button_secondary_text_disabled));
    addItemDecoration(new EmojiItemDecoration(allowVariations, drawable));
  }

  public void presentForEmojiKeyboard() {
    setPadding(getPaddingLeft(),
               getPaddingTop(),
               getPaddingRight(),
               getPaddingBottom() + ViewUtil.dpToPx(56));

    setClipToPadding(false);
  }

  public void onSelected() {
    if (getAdapter() != null) {
      getAdapter().notifyDataSetChanged();
    }
  }

  public void setList(@NonNull List<MappingModel<?>> list, @Nullable Runnable commitCallback) {
    EmojiPageViewGridAdapter adapter = adapterFactory.create();
    setAdapter(adapter);
    adapter.submitList(list, commitCallback);
  }

  @Override
  protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
    if (visibility != VISIBLE) {
      popup.dismiss();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    if (layoutManager instanceof GridLayoutManager) {
      int viewWidth  = w - getPaddingStart() - getPaddingEnd();
      int idealWidth = getContext().getResources().getDimensionPixelOffset(R.dimen.emoji_drawer_item_width);
      int spanCount  = Math.max(viewWidth / idealWidth, 1);

      ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
    }
  }

  @Override
  public void onVariationSelectorStateChanged(boolean open) {
    if (open) {
      addOnItemTouchListener(scrollDisabler);
    } else {
      post(() -> removeOnItemTouchListener(scrollDisabler));
    }

    if (variationSelectorListener != null) {
      variationSelectorListener.onVariationSelectorStateChanged(open);
    }
  }

  public void setRecyclerNestedScrollingEnabled(boolean enabled) {
    setNestedScrollingEnabled(enabled);
  }

  public void smoothScrollToPositionTop(int position) {
    int     currentPosition = layoutManager.findFirstCompletelyVisibleItemPosition();
    boolean shortTrip       = Math.abs(currentPosition - position) < 475;

    if (shortTrip) {
      RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
        @Override
        protected int getVerticalSnapPreference() {
          return LinearSmoothScroller.SNAP_TO_START;
        }
      };
      smoothScroller.setTargetPosition(position);
      layoutManager.startSmoothScroll(smoothScroller);
    } else {
      layoutManager.scrollToPositionWithOffset(position, 0);
    }
  }

  public @Nullable EmojiPageViewGridAdapter getAdapter() {
    return (EmojiPageViewGridAdapter) super.getAdapter();
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

  private interface AdapterFactory {
    EmojiPageViewGridAdapter create();
  }
}
