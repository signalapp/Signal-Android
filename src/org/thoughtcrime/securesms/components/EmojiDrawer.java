package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Emoji;

import java.io.IOException;

public class EmojiDrawer extends FrameLayout {

  private FrameLayout   emojiGridLayout;
  private FrameLayout   recentEmojiGridLayout;

  private EditText      composeText;

  public EmojiDrawer(Context context) {
    super(context);
    initialize();
  }

  public EmojiDrawer(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

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
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.emoji_drawer, this, true);

    ViewPager     pager         = (ViewPager    ) findViewById(R.id.emoji_pager    );
    PagerTabStrip pagerTabStrip = (PagerTabStrip) findViewById(R.id.emoji_tab_strip);
    this.emojiGridLayout        = (FrameLayout) inflater.inflate(R.layout.emoji_grid_layout, null);
    this.recentEmojiGridLayout  = (FrameLayout) inflater.inflate(R.layout.emoji_grid_layout, null);
    GridView emojiGrid          = (GridView) emojiGridLayout.findViewById(R.id.emoji);
    GridView recentEmojiGrid    = (GridView) recentEmojiGridLayout.findViewById(R.id.emoji);

    pagerTabStrip.setBackgroundColor(Color.parseColor("#ff333333"));
    pagerTabStrip.setTextSpacing(1);
    pager.setBackgroundColor(Color.parseColor("#ff333333"));
    emojiGrid.setAdapter(new AllEmojiGridAdapter());
    emojiGrid.setOnItemClickListener(new EmojiClickListener());
    pager.setAdapter(new EmojiPagerAdapter());
    pager.setCurrentItem(1);
  }

  private class EmojiClickListener implements AdapterView.OnItemClickListener {
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      int    start = composeText.getSelectionStart();
      int    end   = composeText.getSelectionEnd  ();
      String emoji = Emoji.EMOJI_ASSET_CODE_MAP.get(Emoji.EMOJI_ASSETS.get(position));

      composeText.getText().replace(Math.min(start, end), Math.max(start, end),
                                    emoji, 0, emoji.length());

      composeText.setText(Emoji.emojify(getContext(), composeText.getText().toString()),
                          TextView.BufferType.SPANNABLE);

      composeText.setSelection(end+2);
    }
  }

  private class AllEmojiGridAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return Emoji.EMOJI_ASSETS.size();
    }

    @Override
    public Object getItem(int position) {
      return null;
    }

    @Override
    public long getItemId(int position) {
      return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      try {
        String asset        = Emoji.EMOJI_ASSETS.get(position);
        Drawable drawable   = Drawable.createFromStream(getContext().getAssets().open(asset + ".png"), null);

        if (convertView != null && convertView instanceof ImageView) {
          ((ImageView)convertView).setImageDrawable(drawable);
          return convertView;
        } else {
          ImageView imageView = new ImageView(getContext());
          imageView.setImageDrawable(drawable);
          return imageView;
        }
      } catch (IOException ioe) {
        throw new AssertionError(ioe);
      }
    }
  }


  private class EmojiPagerAdapter extends PagerAdapter {

    @Override
    public int getCount() {
      return 2;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      switch (position) {
        case 0:
          SpannableString recent      = new SpannableString(" Recent ");
          ImageSpan       recentImage = new ImageSpan(getContext(), R.drawable.ic_emoji_recent_light,
                                                      ImageSpan.ALIGN_BASELINE);

          recent.setSpan(recentImage, 1, recent.length()-1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

          return recent;
        case 1:
          SpannableString emoji      = new SpannableString(" Emoji ");
          ImageSpan       emojiImage = new ImageSpan(getContext(), R.drawable.ic_emoji_light,
                                                     ImageSpan.ALIGN_BASELINE);

          emoji.setSpan(emojiImage, 1, emoji.length()-1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

          return emoji;
        default:
          throw new AssertionError("Bad position!");
      }
    }

    @Override
    public boolean isViewFromObject(View view, Object o) {
      return view == o;
    }

    public Object instantiateItem(ViewGroup container, int position) {
      View view;

      switch (position) {
        case 0:  view = recentEmojiGridLayout; break;
        case 1:  view = emojiGridLayout;       break;
        default: throw new AssertionError("Too many positions!");
      }

      container.addView(view, 0);

      return view;
    }
  }
}
