package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.VariationSelectorListener;
import org.thoughtcrime.securesms.logging.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import com.astuetz.PagerSlidingTabStrip;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.InputAwareLayout.InputView;
import org.thoughtcrime.securesms.components.RepeatableImageKey;
import org.thoughtcrime.securesms.components.RepeatableImageKey.KeyEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView.EmojiSelectionListener;
import org.thoughtcrime.securesms.util.ResUtil;

import java.util.LinkedList;
import java.util.List;

public class EmojiDrawer extends LinearLayout implements InputView, EmojiSelectionListener, VariationSelectorListener {

  private static final String TAG = EmojiDrawer.class.getSimpleName();

  private static final KeyEvent DELETE_KEY_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);

  private ViewPager            pager;
  private List<EmojiPageModel> models;
  private PagerSlidingTabStrip strip;
  private RecentEmojiPageModel recentModel;
  private EmojiEventListener   listener;
  private EmojiDrawerListener  drawerListener;

  public EmojiDrawer(Context context) {
    this(context, null);
  }

  public EmojiDrawer(Context context, AttributeSet attrs) {
    super(context, attrs);
    setOrientation(VERTICAL);
  }

  private void initView() {
    final View v = LayoutInflater.from(getContext()).inflate(R.layout.emoji_drawer, this, true);
    initializeResources(v);
    initializePageModels();
    initializeEmojiGrid();
  }

  public void setEmojiEventListener(EmojiEventListener listener) {
    this.listener = listener;
  }

  public void setDrawerListener(EmojiDrawerListener listener) {
    this.drawerListener = listener;
  }

  private void initializeResources(View v) {
    Log.i(TAG, "initializeResources()");
    this.pager     = (ViewPager)            v.findViewById(R.id.emoji_pager);
    this.strip     = (PagerSlidingTabStrip) v.findViewById(R.id.tabs);

    RepeatableImageKey backspace = (RepeatableImageKey)v.findViewById(R.id.backspace);
    backspace.setOnKeyEventListener(new KeyEventListener() {
      @Override
      public void onKeyEvent() {
        if (listener != null) listener.onKeyEvent(DELETE_KEY_EVENT);
      }
    });
  }

  @Override
  public boolean isShowing() {
    return getVisibility() == VISIBLE;
  }

  @Override
  public void show(int height, boolean immediate) {
    if (this.pager == null) initView();
    ViewGroup.LayoutParams params = getLayoutParams();
    params.height = height;
    Log.i(TAG, "showing emoji drawer with height " + params.height);
    setLayoutParams(params);
    setVisibility(VISIBLE);
    if (drawerListener != null) drawerListener.onShown();
  }

  @Override
  public void hide(boolean immediate) {
    setVisibility(GONE);
    if (drawerListener != null) drawerListener.onHidden();
    Log.i(TAG, "hide()");
  }

  @Override
  public void onEmojiSelected(String emoji) {
    recentModel.onCodePointSelected(emoji);
    if (listener != null) {
      listener.onEmojiSelected(emoji);
    }
  }

  @Override
  public void onVariationSelectorStateChanged(boolean open) {
    pager.setEnabled(!open);
  }

  private void initializeEmojiGrid() {
    pager.setAdapter(new EmojiPagerAdapter(getContext(), models, this, this));

    if (recentModel.getDisplayEmoji().size() == 0) {
      pager.setCurrentItem(1);
    }
    strip.setViewPager(pager);
  }

  private void initializePageModels() {
    this.models = new LinkedList<>();
    this.recentModel = new RecentEmojiPageModel(getContext());
    this.models.add(recentModel);
    this.models.addAll(EmojiPages.DISPLAY_PAGES);
  }

  public static class EmojiPagerAdapter extends PagerAdapter
      implements PagerSlidingTabStrip.CustomTabProvider
  {
    private Context                   context;
    private List<EmojiPageModel>      pages;
    private EmojiSelectionListener    emojiSelectionListener;
    private VariationSelectorListener variationSelectorListener;

    public EmojiPagerAdapter(@NonNull Context context,
                             @NonNull List<EmojiPageModel> pages,
                             @NonNull EmojiSelectionListener emojiSelectionListener,
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
      EmojiPageView page = new EmojiPageView(context, emojiSelectionListener, variationSelectorListener);
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

    @Override
    public View getCustomTabView(ViewGroup viewGroup, int i) {
      ImageView  image = new ImageView(context);
      image.setScaleType(ScaleType.CENTER_INSIDE);
      image.setImageResource(ResUtil.getDrawableRes(context, pages.get(i).getIconAttr()));
      return image;
    }
  }

  public interface EmojiEventListener extends EmojiSelectionListener {
    void onKeyEvent(KeyEvent keyEvent);
  }

  public interface EmojiDrawerListener {
    void onShown();
    void onHidden();
  }
}
