package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.astuetz.PagerSlidingTabStrip;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;
import org.thoughtcrime.securesms.components.RepeatableImageKey;
import org.thoughtcrime.securesms.components.RepeatableImageKey.KeyEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView.EmojiSelectionListener;
import org.thoughtcrime.securesms.util.ResUtil;

import java.util.LinkedList;
import java.util.List;

public class EmojiDrawer extends KeyboardAwareLinearLayout {
  private static final KeyEvent DELETE_KEY_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);

  private EmojiEditText             composeText;
  private KeyboardAwareLinearLayout container;
  private ViewPager                 pager;
  private List<EmojiPageModel>      models;
  private PagerSlidingTabStrip      strip;
  private RecentEmojiPageModel      recentModel;

  public EmojiDrawer(Context context) {
    super(context);
    init();
  }

  public EmojiDrawer(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EmojiDrawer(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  public void setComposeEditText(EmojiEditText composeText) {
    this.composeText = composeText;
  }

  private void init() {
    final View v = LayoutInflater.from(getContext()).inflate(R.layout.emoji_drawer, this, true);
    initializeResources(v);
    initializePageModels();
    initializeEmojiGrid();
  }

  private void initializeResources(View v) {
    Log.w("EmojiDrawer", "initializeResources()");
    this.container = (KeyboardAwareLinearLayout) v.findViewById(R.id.container);
    this.pager     = (ViewPager)                 v.findViewById(R.id.emoji_pager);
    this.strip     = (PagerSlidingTabStrip)      v.findViewById(R.id.tabs);

    RepeatableImageKey backspace = (RepeatableImageKey)v.findViewById(R.id.backspace);
    backspace.setOnKeyEventListener(new KeyEventListener() {
      @Override public void onKeyEvent() {
        if (composeText != null && composeText.getText().length() > 0) {
          composeText.dispatchKeyEvent(DELETE_KEY_EVENT);
        }
      }
    });
  }

  public void hide() {
    container.setVisibility(View.GONE);
  }

  public void show() {
    int keyboardHeight = container.getKeyboardHeight();
    Log.w("EmojiDrawer", "setting emoji drawer to height " + keyboardHeight);
    container.setLayoutParams(new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, keyboardHeight));
    container.requestLayout();
    container.setVisibility(View.VISIBLE);
  }

  public boolean isOpen() {
    return container.getVisibility() == View.VISIBLE;
  }

  private void initializeEmojiGrid() {
    pager.setAdapter(new EmojiPagerAdapter(getContext(),
                                           models,
                                           new EmojiSelectionListener() {
                                             @Override public void onEmojiSelected(String emoji) {
                                               recentModel.onCodePointSelected(emoji);
                                               composeText.insertEmoji(emoji);
                                             }
                                           }));

    if (recentModel.getEmoji().length == 0) {
      pager.setCurrentItem(1);
    }
    strip.setViewPager(pager);
  }

  private void initializePageModels() {
    this.models = new LinkedList<>();
    this.recentModel = new RecentEmojiPageModel(getContext());
    this.models.add(recentModel);
    this.models.addAll(EmojiPages.PAGES);
  }

  public static class EmojiPagerAdapter extends PagerAdapter
      implements PagerSlidingTabStrip.CustomTabProvider
  {
    private Context                context;
    private List<EmojiPageModel>   pages;
    private EmojiSelectionListener listener;

    public EmojiPagerAdapter(@NonNull Context context,
                             @NonNull List<EmojiPageModel> pages,
                             @Nullable EmojiSelectionListener listener)
    {
      super();
      this.context  = context;
      this.pages    = pages;
      this.listener = listener;
    }

    @Override
    public int getCount() {
      return pages.size();
    }

    @Override public Object instantiateItem(ViewGroup container, int position) {
      EmojiPageView page = new EmojiPageView(context);
      page.setModel(pages.get(position));
      page.setEmojiSelectedListener(listener);
      container.addView(page);
      return page;
    }

    @Override public void destroyItem(ViewGroup container, int position, Object object) {
      container.removeView((View)object);
    }

    @Override public void setPrimaryItem(ViewGroup container, int position, Object object) {
      EmojiPageView current = (EmojiPageView) object;
      current.onSelected();
      super.setPrimaryItem(container, position, object);
    }

    @Override public boolean isViewFromObject(View view, Object object) {
      return view == object;
    }

    @Override public View getCustomTabView(ViewGroup viewGroup, int i) {
      ImageView image = new ImageView(context);
      image.setScaleType(ScaleType.CENTER_INSIDE);
      image.setImageResource(ResUtil.getDrawableRes(context, pages.get(i).getIconAttr()));
      return image;
    }
  }
}
