package org.thoughtcrime.securesms;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import java.util.List;

public class IntroPagerAdapter extends FragmentStatePagerAdapter {

  public static class IntroPage {
    final int      backgroundColor;
    final Fragment fragment;

    public IntroPage(int backgroundColor, Fragment fragment) {
      this.backgroundColor = backgroundColor;
      this.fragment        = fragment;
    }
  }

  private List<IntroPage> pages;

  public IntroPagerAdapter(FragmentManager fm, List<IntroPage> pages) {
    super(fm);
    this.pages = pages;
  }

  @Override
  public Fragment getItem(int i) {
    IntroPage page = pages.get(i);
    return page.fragment;
  }

  @Override
  public int getCount() {
    return pages.size();
  }
}
