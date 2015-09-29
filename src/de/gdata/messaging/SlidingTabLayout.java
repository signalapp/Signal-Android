package de.gdata.messaging;

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

    import android.content.Context;
    import android.graphics.Typeface;
    import android.os.Build;
    import android.support.v4.view.PagerAdapter;
    import android.support.v4.view.ViewPager;
    import android.util.AttributeSet;
    import android.util.TypedValue;
    import android.view.Gravity;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.HorizontalScrollView;
    import android.widget.ImageView;
    import android.widget.TextView;

    import org.thoughtcrime.securesms.R;
    import org.thoughtcrime.securesms.database.DatabaseFactory;

/**
 * To be used with ViewPager to provide a tab indicator component which give constant feedback as to
 * the user's scroll progress.
 * <p>
 * To use the component, simply add it to your view hierarchy. Then in your
 * {@link android.app.Activity} or {@link android.support.v4.app.Fragment} call
 * {@link #setViewPager(ViewPager)} providing it the ViewPager this layout is being used for.
 * <p>
 * The colors can be customized in two ways. The first and simplest is to provide an array of colors
 * via {@link #setSelectedIndicatorColors(int...)} and {@link #setDividerColors(int...)}. The
 * alternative is via the {@link TabColorizer} interface which provides you complete control over
 * which color is used for any individual position.
 * <p>
 * The views used as tabs can be customized by calling {@link #setCustomTabView(int, int)},
 * providing the layout ID of your custom layout.
 */
public class SlidingTabLayout extends HorizontalScrollView {

  /**
   * Allows complete control over the colors drawn in the tab layout. Set with
   * {@link #setCustomTabColorizer(TabColorizer)}.
   */
  public interface TabColorizer {

    /**
     * @return return the color of the indicator used when {@code position} is selected.
     */
    int getIndicatorColor(int position);

    /**
     * @return return the color of the divider drawn to the right of {@code position}.
     */
    int getDividerColor(int position);

  }

  private static final int TITLE_OFFSET_DIPS = 24;
  private static final int TAB_VIEW_PADDING_DIPS = 16;
  private static final int TAB_VIEW_TEXT_SIZE_SP = 12;

  private int mTitleOffset;

  private int mTabViewLayoutId;
  private int mTabViewTextViewId;

  private ViewPager mViewPager;
  private ViewPager.OnPageChangeListener mViewPagerPageChangeListener;

  private final SlidingTabStrip mTabStrip;

  public SlidingTabLayout(Context context) {
    this(context, null);
  }

  public SlidingTabLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SlidingTabLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    // Disable the Scroll Bar
    setHorizontalScrollBarEnabled(false);
    // Make sure that the Tab Strips fills this View
    setFillViewport(true);

    mTitleOffset = (int) (TITLE_OFFSET_DIPS * getResources().getDisplayMetrics().density);

    mTabStrip = new SlidingTabStrip(context);
    addView(mTabStrip, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
  }

  /**
   * Set the custom {@link TabColorizer} to be used.
   *
   * If you only require simple custmisation then you can use
   * {@link #setSelectedIndicatorColors(int...)} and {@link #setDividerColors(int...)} to achieve
   * similar effects.
   */
  public void setCustomTabColorizer(TabColorizer tabColorizer) {
    mTabStrip.setCustomTabColorizer(tabColorizer);
  }

  /**
   * Sets the colors to be used for indicating the selected tab. These colors are treated as a
   * circular array. Providing one color will mean that all tabs are indicated with the same color.
   */
  public void setSelectedIndicatorColors(int... colors) {
    mTabStrip.setSelectedIndicatorColors(colors);
  }

  /**
   * Sets the colors to be used for tab dividers. These colors are treated as a circular array.
   * Providing one color will mean that all tabs are indicated with the same color.
   */
  public void setDividerColors(int... colors) {
    mTabStrip.setDividerColors(colors);
  }

  /**
   * Set the {@link ViewPager.OnPageChangeListener}. When using {@link SlidingTabLayout} you are
   * required to set any {@link ViewPager.OnPageChangeListener} through this method. This is so
   * that the layout can update it's scroll position correctly.
   *
   * @see ViewPager#setOnPageChangeListener(ViewPager.OnPageChangeListener)
   */
  public void setOnPageChangeListener(ViewPager.OnPageChangeListener listener) {
    mViewPagerPageChangeListener = listener;
  }

  /**
   * Set the custom layout to be inflated for the tab views.
   *
   * @param layoutResId Layout id to be inflated
   * @param textViewId id of the {@link TextView} in the inflated view
   */
  public void setCustomTabView(int layoutResId, int textViewId) {
    mTabViewLayoutId = layoutResId;
    mTabViewTextViewId = textViewId;
  }

  /**
   * Sets the associated view pager. Note that the assumption here is that the pager content
   * (number of tabs and tab titles) does not change after this call has been made.
   */
  public void setViewPager(ViewPager viewPager) {
    mTabStrip.removeAllViews();

    mViewPager = viewPager;
    if (viewPager != null) {
      viewPager.setOnPageChangeListener(new InternalViewPagerListener());
      populateTabStrip();
    }
  }

  /**
   * Create a default view to be used for tabs. This is called if a custom tab view is not set via
   * {@link #setCustomTabView(int, int)}.
   */
  protected TextView createDefaultTabView(Context context) {
    TextView textView = new TextView(context);
    textView.setGravity(Gravity.CENTER);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, TAB_VIEW_TEXT_SIZE_SP);
    textView.setTypeface(Typeface.DEFAULT_BOLD);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      // If we're running on Honeycomb or newer, then we can use the Theme's
      // selectableItemBackground to ensure that the View has a pressed state
      TypedValue outValue = new TypedValue();
      getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
          outValue, true);
      textView.setBackgroundResource(outValue.resourceId);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      // If we're running on ICS or newer, enable all-caps to match the Action Bar tab style
      textView.setAllCaps(true);
    }

    int padding = (int) (TAB_VIEW_PADDING_DIPS * getResources().getDisplayMetrics().density);
    textView.setPadding(padding, padding, padding, padding);

    return textView;
  }

  private void populateTabStrip() {
    final PagerAdapter adapter = mViewPager.getAdapter();
    final View.OnClickListener tabClickListener = new TabClickListener();

    for (int i = 0; i < adapter.getCount(); i++) {
      View tabView = null;

      tabView = LayoutInflater.from(getContext()).inflate(R.layout.custom_tab_title, mTabStrip,
          false);

      ImageView iconImageView = (ImageView) tabView.findViewById(R.id.tab_layout_icon);
      TextView counter = (TextView) tabView.findViewById(R.id.tab_layout_count);
      if(i==0) {
        int unreadCount = DatabaseFactory.getMmsDatabase(getContext()).getUnreadThreadsCount();
        if(unreadCount > 0) {
          counter.setText(unreadCount+"");
        } else {
          counter.setVisibility(View.GONE);
        }
      } else {
        counter.setVisibility(View.GONE);
      }

      iconImageView.setImageDrawable(getContext().getResources().getDrawable(getIconResourceArray()[i]));

      tabView.setOnClickListener(tabClickListener);

      mTabStrip.addView(tabView);
    }
  }
  private Integer[] mIconResourceArray;

  public Integer[] getIconResourceArray() {
    return mIconResourceArray;
  }

  public void setIconResourceArray(Integer[] mIconResourceArray) {
    this.mIconResourceArray = mIconResourceArray;
  }
  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    if (mViewPager != null) {
      scrollToTab(mViewPager.getCurrentItem(), 0);
    }
  }

  private void scrollToTab(int tabIndex, int positionOffset) {
    final int tabStripChildCount = mTabStrip.getChildCount();
    if (tabStripChildCount == 0 || tabIndex < 0 || tabIndex >= tabStripChildCount) {
      return;
    }

    View selectedChild = mTabStrip.getChildAt(tabIndex);
    if (selectedChild != null) {
      int targetScrollX = selectedChild.getLeft() + positionOffset;

      if (tabIndex > 0 || positionOffset > 0) {
        // If we're not at the first child and are mid-scroll, make sure we obey the offset
        targetScrollX -= mTitleOffset;
      }

      scrollTo(targetScrollX, 0);
    }
  }

  private class InternalViewPagerListener implements ViewPager.OnPageChangeListener {
    private int mScrollState;

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      int tabStripChildCount = mTabStrip.getChildCount();
      if ((tabStripChildCount == 0) || (position < 0) || (position >= tabStripChildCount)) {
        return;
      }

      mTabStrip.onViewPagerPageChanged(position, positionOffset);

      View selectedTitle = mTabStrip.getChildAt(position);
      int extraOffset = (selectedTitle != null)
          ? (int) (positionOffset * selectedTitle.getWidth())
          : 0;
      scrollToTab(position, extraOffset);

      if (mViewPagerPageChangeListener != null) {
        mViewPagerPageChangeListener.onPageScrolled(position, positionOffset,
            positionOffsetPixels);
      }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
      mScrollState = state;

      if (mViewPagerPageChangeListener != null) {
        mViewPagerPageChangeListener.onPageScrollStateChanged(state);
      }
    }

    @Override
    public void onPageSelected(int position) {
      if (mScrollState == ViewPager.SCROLL_STATE_IDLE) {
        mTabStrip.onViewPagerPageChanged(position, 0f);
        scrollToTab(position, 0);
      }

      if (mViewPagerPageChangeListener != null) {
        mViewPagerPageChangeListener.onPageSelected(position);
      }
    }

  }

  private class TabClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      for (int i = 0; i < mTabStrip.getChildCount(); i++) {
        if (v == mTabStrip.getChildAt(i)) {
          mViewPager.setCurrentItem(i);
          return;
        }
      }
    }
  }

}