package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.astuetz.PagerSlidingTabStrip;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Emoji;

public class EmojiDrawer extends KeyboardAwareLinearLayout {

  private static final int RECENT_TYPE = 0;
  private static final int ALL_TYPE    = 1;

  private FrameLayout[]        gridLayouts = new FrameLayout[Emoji.PAGES.length+1];
  private EditText             composeText;
  private Emoji                emoji;
  private ViewPager            pager;
  private PagerSlidingTabStrip strip;
  private ImageButton          backspace;

  @SuppressWarnings("unused")
  public EmojiDrawer(Context context) {
    super(context);
    initialize();
  }

  @SuppressWarnings("unused")
  public EmojiDrawer(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  @SuppressWarnings("unused")
  @TargetApi(VERSION_CODES.HONEYCOMB)
  public EmojiDrawer(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setComposeEditText(EditText composeText) {
    this.composeText = composeText;
  }

  public boolean isOpen() {
    return getVisibility() == View.VISIBLE;
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.emoji_drawer, this, true);

    initializeResources();
    initializeEmojiGrid();
  }

  private void initializeResources() {
    this.pager     = (ViewPager                ) findViewById(R.id.emoji_pager);
    this.strip     = (PagerSlidingTabStrip     ) findViewById(R.id.tabs);
    this.backspace = (ImageButton              ) findViewById(R.id.backspace);
    this.emoji     = Emoji.getInstance(getContext());

    this.backspace.setOnClickListener(new BackspaceClickListener());
  }

  public void hide() {
    setVisibility(View.GONE);
  }

  public void show() {
    int keyboardHeight = getKeyboardHeight();
    Log.w("EmojiDrawer", "setting emoji drawer to height " + keyboardHeight);
    setLayoutParams(new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, keyboardHeight));
    requestLayout();
    setVisibility(View.VISIBLE);
  }

  private void initializeEmojiGrid() {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    for (int i = 0; i < gridLayouts.length; i++) {
      gridLayouts[i]    = (FrameLayout) inflater.inflate(R.layout.emoji_grid_layout, pager, false);
      final GridView gridView = (GridView) gridLayouts[i].findViewById(R.id.emoji);
      gridLayouts[i].setTag(gridView);
      final int      type     = (i == 0 ? RECENT_TYPE : ALL_TYPE);
      gridView.setColumnWidth(getResources().getDimensionPixelSize(R.dimen.emoji_drawer_size) + 2*getResources().getDimensionPixelSize(R.dimen.emoji_drawer_item_padding));
      gridView.setAdapter(new EmojiGridAdapter(type, i-1));
      gridView.setOnItemClickListener(new EmojiClickListener(ALL_TYPE));
    }

    pager.setAdapter(new EmojiPagerAdapter());

    if (emoji.getRecentlyUsedAssetCount() <= 0) {
      pager.setCurrentItem(1);
    }
    strip.setTabPaddingLeftRight(getResources().getDimensionPixelSize(R.dimen.emoji_drawer_left_right_padding));
    strip.setAllCaps(false);
    strip.setShouldExpand(true);
    strip.setUnderlineColorResource(R.color.emoji_tab_underline);
    strip.setIndicatorColorResource(R.color.emoji_tab_indicator);
    strip.setIndicatorHeight(getResources().getDimensionPixelSize(R.dimen.emoji_drawer_indicator_height));

    strip.setViewPager(pager);
  }

  private class EmojiClickListener implements AdapterView.OnItemClickListener {

    private final int type;

    public EmojiClickListener(int type) {
      this.type = type;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      Integer unicodePoint = (Integer) view.getTag();
      insertEmoji(composeText, unicodePoint);
      if (type != RECENT_TYPE) {
        emoji.setRecentlyUsed(Integer.toHexString(unicodePoint));
        ((BaseAdapter)((GridView)gridLayouts[0].getTag()).getAdapter()).notifyDataSetChanged();
      }
    }

    private void insertEmoji(EditText editText, Integer unicodePoint) {
      final char[] chars = Character.toChars(unicodePoint);
      String characters = new String(chars);
      int start = editText.getSelectionStart();
      int end   = editText.getSelectionEnd();

      CharSequence text = emoji.emojify(characters, new Emoji.InvalidatingPageLoadedListener(composeText));
      editText.getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());

      editText.setSelection(end+chars.length);
    }
  }

  private class BackspaceClickListener implements OnClickListener {

    private final KeyEvent deleteKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);

    @Override
    public void onClick(View v) {
      if (composeText.getText().length() > 0) {
        composeText.dispatchKeyEvent(deleteKeyEvent);
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
      }
    }

  }

  private class EmojiGridAdapter extends BaseAdapter {

    private final int type;
    private final int page;
    private final int emojiSize;

    public EmojiGridAdapter(int type, int page) {
      this.type  = type;
      this.page  = page;
      emojiSize  = (int) getResources().getDimension(R.dimen.emoji_drawer_size);
    }

    @Override
    public int getCount() {
      if (type == RECENT_TYPE) return emoji.getRecentlyUsedAssetCount();
      else                     return Emoji.PAGES[page].length;
    }

    @Override
    public Object getItem(int position) {
      return null;
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
      final ImageView view;
      final int pad = getResources().getDimensionPixelSize(R.dimen.emoji_drawer_item_padding);
      if (convertView != null && convertView instanceof ImageView) {
        view = (ImageView) convertView;
      } else {
        ImageView imageView = new ImageView(getContext());
        imageView.setPadding(pad, pad, pad, pad);
        imageView.setLayoutParams(new AbsListView.LayoutParams(emojiSize + 2*pad, emojiSize + 2*pad));
        view = imageView;
      }

      final Drawable drawable;
      final Integer  unicodeTag;
      if (type == ALL_TYPE) {
        unicodeTag = Emoji.PAGES[page][position];
        drawable   = emoji.getEmojiDrawable(new Emoji.DrawInfo(page, position),
                                            Emoji.EMOJI_HUGE,
                                            new Emoji.InvalidatingPageLoadedListener(view));
      } else {
        Pair<Integer, Drawable> recentlyUsed = emoji.getRecentlyUsed(position,
                                                                     Emoji.EMOJI_HUGE,
                                                                     new Emoji.InvalidatingPageLoadedListener(view));
        unicodeTag = recentlyUsed.first;
        drawable   = recentlyUsed.second;
      }

      view.setImageDrawable(drawable);
      view.setPadding(pad, pad, pad, pad);
      view.setTag(unicodeTag);
      return view;
    }
  }

  private class EmojiPagerAdapter extends PagerAdapter implements PagerSlidingTabStrip.IconTabProvider {

    @Override
    public int getCount() {
      return gridLayouts.length;
    }

    @Override
    public boolean isViewFromObject(View view, Object o) {
      return view == o;
    }

    public Object instantiateItem(ViewGroup container, int position) {
      if (position < 0 || position >= gridLayouts.length)
        throw new AssertionError("position out of range!");

      container.addView(gridLayouts[position], 0);

      return gridLayouts[position];
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
      Log.w("EmojiDrawer", "destroying item at " + position);
      container.removeView(gridLayouts[position]);
    }

    @Override
    public int getPageIconResId(int i) {
      switch (i) {
      case 0: return R.drawable.emoji_category_recent;
      case 1: return R.drawable.emoji_category_smile;
      case 2: return R.drawable.emoji_category_flower;
      case 3: return R.drawable.emoji_category_bell;
      case 4: return R.drawable.emoji_category_car;
      case 5: return R.drawable.emoji_category_symbol;
      default: return 0;
      }
    }
  }
}
