package org.thoughtcrime.securesms.components.mp02anim;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

public class ItemAnimView<T extends ViewGroup> implements ItemAnimViewController.AnimyAction {

  private View item;
  private TextView tempTitle1;
  private TextView tempTitle2;

  private Animation animUpAndVisible;
  private Animation animUpAndGone;
  private Animation animDownAndVisible;
  private Animation animDownAndGone;

  private int mFocusTextSize;
  private int mItemHeight;

  public ItemAnimView(T layoutView, int textSize, int itemHeight, int marginTop) {
    Context cxt = layoutView.getContext();
    if (layoutView instanceof FrameLayout || layoutView instanceof RelativeLayout) {
      this.mFocusTextSize = textSize;
      this.mItemHeight = itemHeight;
      animUpAndVisible = AnimationUtils.loadAnimation(cxt, R.anim.mp02_up_visible);
      animUpAndGone = AnimationUtils.loadAnimation(cxt, R.anim.mp02_up_gone);
      animDownAndVisible = AnimationUtils.loadAnimation(cxt, R.anim.mp02_down_visible);
      animDownAndGone = AnimationUtils.loadAnimation(cxt, R.anim.mp02_down_gone);
      bindView(layoutView, marginTop);
    } else {
      try {
        throw new IllegalAccessException("the parameter T is not fit");
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }
    setItemVisibility(true);
  }

  protected void bindView(T layoutView, int marginTop) {
    item = LayoutInflater.from(layoutView.getContext()).inflate(R.layout.mp02_tmp_layout, null);

    tempTitle1 = item.findViewById(R.id.temp_item_name);
    tempTitle2 = item.findViewById(R.id.temp_item_name2);
    tempTitle1.setTextSize(mFocusTextSize);
    tempTitle2.setTextSize(mFocusTextSize);

    if (layoutView instanceof FrameLayout) {
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
      lp.setMargins(0, marginTop, 0, 0);
      layoutView.addView(item, lp);
      lp.height = mItemHeight;
    } else {
      RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
      lp.setMargins(0, marginTop, 0, 0);
      lp.height = mItemHeight;
      layoutView.addView(item, lp);
    }
  }


  @Override
  public void upIn(String title1, String title2) {
    setItemVisibility(false);
    tempTitle1.setText(title1);
    tempTitle2.setText(title2);
    tempTitle2.startAnimation(animUpAndVisible);

    setOutVisibility(false);
    setEdtVisibility(true);
    tempTitle1.startAnimation(animUpAndGone);
    animUpAndGone.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        setOutVisibility(true);
        setItemVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
  }


  @Override
  public void downIn(String title1, String title2) {
    setItemVisibility(false);

    tempTitle1.setText(title1);
    tempTitle2.setText(title2);
    tempTitle2.startAnimation(animDownAndVisible);
    setOutVisibility(false);
    setEdtVisibility(true);
    tempTitle1.startAnimation(animDownAndGone);
    animDownAndGone.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        setOutVisibility(true);
        setItemVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
  }

  @Override
  public void downInWithEdt(String title1, String title2) {
    setItemVisibility(false);
    tempTitle1.setText(title1);
    setEdtVisibility(false);
    setOutVisibility(false);
    tempTitle1.startAnimation(animDownAndGone);
    animDownAndGone.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        setOutVisibility(true);
        setItemVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
  }

  @Override
  public void editTextChange(String txt) {
  }

  @Override
  public void setInVisibility(boolean isGone) {
    tempTitle2.setVisibility(isGone ? View.GONE : View.VISIBLE);
  }

  @Override
  public void setOutVisibility(boolean isGone) {
    tempTitle1.setVisibility(isGone ? View.GONE : View.VISIBLE);
  }

  @Override
  public void setEdtVisibility(boolean isGone) {
//        tempEdt.setVisibility( View.VISIBLE );
  }

  @Override
  public void setItemVisibility(boolean isGone) {
    item.setVisibility(isGone ? View.GONE : View.VISIBLE);
  }

  @Override
  public void setItemText(String text) {
    tempTitle1.setText(text);
    tempTitle2.setText(text);
  }
}