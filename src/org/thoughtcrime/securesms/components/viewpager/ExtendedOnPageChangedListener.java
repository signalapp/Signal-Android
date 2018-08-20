package org.thoughtcrime.securesms.components.viewpager;


import android.support.v4.view.ViewPager;

public abstract class ExtendedOnPageChangedListener implements ViewPager.OnPageChangeListener {

  private Integer currentPage = null;

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

  }

  @Override
  public void onPageSelected(int position) {
    if (currentPage != null && currentPage != position) onPageUnselected(currentPage);
    currentPage = position;
  }

  public abstract void onPageUnselected(int position);

  @Override
  public void onPageScrollStateChanged(int state) {

  }


}
