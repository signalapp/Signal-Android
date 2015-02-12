package org.thoughtcrime.securesms;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class IntroPagerAdapter extends FragmentStatePagerAdapter {

  private static final int[] drawables = new int[] {
      R.drawable.onboard_logo,
      R.drawable.onboard_face,
      R.drawable.onboard_message
  };

  private static final int[] texts = new int[] {
      R.string.IntroFragment_one_title,
      R.string.IntroFragment_two_title,
      R.string.IntroFragment_three_title
  };

  private static final int[] subtexts = new int[] {
      R.string.IntroFragment_one_subtitle,
      R.string.IntroFragment_two_subtitle,
      R.string.IntroFragment_three_subtitle
  };


  public IntroPagerAdapter(FragmentManager fm) {
    super(fm);
  }

  @Override
  public Fragment getItem(int i) {
    return IntroFragment.newInstance(drawables[i], texts[i], subtexts[i]);
  }

  @Override
  public int getCount() {
    return 3;
  }
}
