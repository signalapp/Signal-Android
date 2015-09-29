package org.thoughtcrime.securesms;

import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import java.util.List;

public class IntroPagerAdapter extends FragmentStatePagerAdapter {

  public static class IntroModel {
                 int backgroundColor;
    @DrawableRes int drawableRes;
    @StringRes   int titleRes;
    @StringRes   int subtextRes;

    public IntroModel(int backgroundColor,
                      @DrawableRes int drawableRes,
                      @StringRes   int titleRes,
                      @StringRes   int subtextRes)
    {
      this.backgroundColor = backgroundColor;
      this.drawableRes     = drawableRes;
      this.titleRes        = titleRes;
      this.subtextRes      = subtextRes;
    }
  }

  private List<IntroModel> models;

  public IntroPagerAdapter(FragmentManager fm, List<IntroModel> models) {
    super(fm);
    this.models = models;
  }

  @Override
  public Fragment getItem(int i) {
    IntroModel model = models.get(i);
    return IntroFragment.newInstance(model.drawableRes, model.titleRes, model.subtextRes);
  }

  @Override
  public int getCount() {
    return models.size();
  }
}
