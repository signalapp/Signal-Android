package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.VariationSelectorListener;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.ResUtil;

import java.util.LinkedList;
import java.util.List;

/**
 * A provider to select emoji in the {@link org.thoughtcrime.securesms.components.emoji.MediaKeyboard}.
 */
public class EmojiKeyboardProvider implements MediaKeyboardProvider,
                                              MediaKeyboardProvider.TabIconProvider,
                                              MediaKeyboardProvider.BackspaceObserver,
                                              VariationSelectorListener
{
  private static final KeyEvent DELETE_KEY_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);

  private static final String RECENT_STORAGE_KEY = "pref_recent_emoji2";

  private final Context              context;
  private final List<EmojiPageModel> models;
  private final RecentEmojiPageModel recentModel;
  private final EmojiPagerAdapter    emojiPagerAdapter;
  private final EmojiEventListener   emojiEventListener;

  private Controller controller;
  private int        currentPosition;

  public EmojiKeyboardProvider(@NonNull Context context, @Nullable EmojiEventListener emojiEventListener) {
    this.context            = context;
    this.emojiEventListener = emojiEventListener;
    this.models             = new LinkedList<>();
    this.recentModel        = new RecentEmojiPageModel(context, RECENT_STORAGE_KEY);
    this.emojiPagerAdapter  = new EmojiPagerAdapter(context, models, new EmojiEventListener() {
      @Override
      public void onEmojiSelected(String emoji) {
        recentModel.onCodePointSelected(emoji);
        SignalStore.emojiValues().setPreferredVariation(emoji);

        if (emojiEventListener != null) {
          emojiEventListener.onEmojiSelected(emoji);
        }
      }

      @Override
      public void onKeyEvent(KeyEvent keyEvent) {
        if (emojiEventListener != null) {
          emojiEventListener.onKeyEvent(keyEvent);
        }
      }
    }, this);

    models.add(recentModel);
    models.addAll(EmojiPages.DISPLAY_PAGES);

    currentPosition = recentModel.getEmoji().size() > 0 ? 0 : 1;
  }

  @Override
  public void requestPresentation(@NonNull Presenter presenter, boolean isSoloProvider) {
    presenter.present(this, emojiPagerAdapter, this, this, null, null, currentPosition);
  }

  @Override
  public void setCurrentPosition(int currentPosition) {
    this.currentPosition = currentPosition;
  }

  @Override
  public void setController(@Nullable Controller controller) {
    this.controller = controller;
  }

  @Override
  public int getProviderIconView(boolean selected) {
    if (selected) {
      return R.layout.emoji_keyboard_icon_selected;
    } else {
      return R.layout.emoji_keyboard_icon;
    }
  }

  @Override
  public void loadCategoryTabIcon(@NonNull GlideRequests glideRequests, @NonNull ImageView imageView, int index) {
    Drawable drawable = ResUtil.getDrawable(context, models.get(index).getIconAttr());
    imageView.setImageDrawable(drawable);
  }

  @Override
  public void onBackspaceClicked() {
    if (emojiEventListener != null) {
      emojiEventListener.onKeyEvent(DELETE_KEY_EVENT);
    }
  }

  @Override
  public void onVariationSelectorStateChanged(boolean open) {
    if (controller != null) {
      controller.setViewPagerEnabled(!open);
    }
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    return obj instanceof EmojiKeyboardProvider;
  }

  private static class EmojiPagerAdapter extends PagerAdapter {
    private Context                   context;
    private List<EmojiPageModel>      pages;
    private EmojiEventListener emojiSelectionListener;
    private VariationSelectorListener variationSelectorListener;

    public EmojiPagerAdapter(@NonNull Context context,
                             @NonNull List<EmojiPageModel> pages,
                             @NonNull EmojiEventListener emojiSelectionListener,
                             @NonNull VariationSelectorListener variationSelectorListener)
    {
      super();
      this.context                   = context;
      this.pages                     = pages;
      this.emojiSelectionListener    = emojiSelectionListener;
      this.variationSelectorListener = variationSelectorListener;
    }

    @Override
    public int getCount() {
      return pages.size();
    }

    @Override
    public @NonNull Object instantiateItem(@NonNull ViewGroup container, int position) {
      EmojiPageView page = new EmojiPageView(context, emojiSelectionListener, variationSelectorListener, true);
      page.setModel(pages.get(position));
      container.addView(page);
      return page;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
      container.removeView((View)object);
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
      EmojiPageView current = (EmojiPageView) object;
      current.onSelected();
      super.setPrimaryItem(container, position, object);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
      return view == object;
    }
  }

  public interface EmojiEventListener {
    void onEmojiSelected(String emoji);
    void onKeyEvent(KeyEvent keyEvent);
  }
}
