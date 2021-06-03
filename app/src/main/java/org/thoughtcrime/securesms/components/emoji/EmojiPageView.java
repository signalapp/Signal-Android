package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.VariationSelectorListener;
import org.thoughtcrime.securesms.util.MappingModelList;
import org.thoughtcrime.securesms.util.ViewUtil;

public class EmojiPageView extends FrameLayout implements VariationSelectorListener {
  private static final String TAG = Log.tag(EmojiPageView.class);

  private EmojiPageModel                   model;
  private AdapterFactory                   adapterFactory;
  private RecyclerView                     recyclerView;
  private RecyclerView.LayoutManager       layoutManager;
  private RecyclerView.OnItemTouchListener scrollDisabler;
  private VariationSelectorListener        variationSelectorListener;
  private EmojiVariationSelectorPopup      popup;

  public EmojiPageView(@NonNull Context context,
                       @NonNull EmojiEventListener emojiSelectionListener,
                       @NonNull VariationSelectorListener variationSelectorListener,
                       boolean allowVariations)
  {
    this(context, emojiSelectionListener, variationSelectorListener, allowVariations, new GridLayoutManager(context, 8), R.layout.emoji_display_item);
  }

  public EmojiPageView(@NonNull Context context,
                       @NonNull EmojiEventListener emojiSelectionListener,
                       @NonNull VariationSelectorListener variationSelectorListener,
                       boolean allowVariations,
                       @NonNull RecyclerView.LayoutManager layoutManager,
                       @LayoutRes int displayItemLayoutResId)
  {
    super(context);
    final View view = LayoutInflater.from(getContext()).inflate(R.layout.emoji_grid_layout, this, true);

    this.variationSelectorListener = variationSelectorListener;

    this.recyclerView   = view.findViewById(R.id.emoji);
    this.layoutManager  = layoutManager;
    this.scrollDisabler = new ScrollDisabler();
    this.popup          = new EmojiVariationSelectorPopup(context, emojiSelectionListener);
    this.adapterFactory = () -> new EmojiPageViewGridAdapter(popup,
                                                             emojiSelectionListener,
                                                             this,
                                                             allowVariations,
                                                             displayItemLayoutResId);

    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setItemAnimator(null);
  }

  public void presentForEmojiKeyboard() {
    recyclerView.setPadding(recyclerView.getPaddingLeft(),
                            recyclerView.getPaddingTop(),
                            recyclerView.getPaddingRight(),
                            recyclerView.getPaddingBottom() + ViewUtil.dpToPx(56));

    recyclerView.setClipToPadding(false);
  }

  public void onSelected() {
    if (model.isDynamic() && recyclerView.getAdapter() != null) {
      recyclerView.getAdapter().notifyDataSetChanged();
    }
  }

  public void setModel(@Nullable EmojiPageModel model) {
    this.model = model;

    EmojiPageViewGridAdapter adapter = adapterFactory.create();
    recyclerView.setAdapter(adapter);
    adapter.submitList(getMappingModelList());
  }

  public void bindSearchableAdapter(@Nullable EmojiPageModel model) {
    this.model = model;

    EmojiPageViewGridAdapter adapter = adapterFactory.create();
    recyclerView.setAdapter(adapter);
    adapter.submitList(getMappingModelList());
  }

  private @NonNull MappingModelList getMappingModelList() {
    MappingModelList mappingModels = new MappingModelList();

    if (model != null) {
      mappingModels.addAll(Stream.of(model.getDisplayEmoji()).map(EmojiPageViewGridAdapter.EmojiModel::new).toList());
    }

    return mappingModels;
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
      int idealWidth  = getContext().getResources().getDimensionPixelOffset(R.dimen.emoji_drawer_item_width);
      int spanCount = Math.max(w / idealWidth, 1);

      ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
    }
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

  public void setRecyclerNestedScrollingEnabled(boolean enabled) {
    recyclerView.setNestedScrollingEnabled(enabled);
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
