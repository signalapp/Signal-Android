package org.thoughtcrime.securesms.components.mp02anim;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
//import com.android.contacts.view.EditText2;
import org.thoughtcrime.securesms.R;
import android.util.Log;


public class ItemAnimView2<T extends ViewGroup> implements ItemAnimViewController2.AnimyAction2 {

  private static final String TAG = ItemAnimView2.class.getSimpleName();

  private View item;
  private TextView tempTitle01;
  private TextView tempTitle02;
  private TextView tempTitle1;
  private TextView tempTitle2;
//    private MyEditView tempEdt;

  private Animation animUpAndVisible;
  private Animation animUpAndGone;
  private Animation animDownAndVisible;
  private Animation animDownAndGone;

  private Animation animUpAndVisible2;
  private Animation animUpAndGone2;
  private Animation animDownAndVisible2;
  private Animation animDownAndGone2;

  private int mFocusTextSize;
  private int mItemHeight;

  public ItemAnimView2(T layoutView, int textSize, int itemHeight, int marginTop) {
    Context cxt = layoutView.getContext();
    if (layoutView instanceof FrameLayout || layoutView instanceof RelativeLayout) {
      this.mFocusTextSize = textSize;
      this.mItemHeight = itemHeight;
      animUpAndVisible = AnimationUtils.loadAnimation(cxt, R.anim.mp02_up_visible);
      animUpAndGone = AnimationUtils.loadAnimation(cxt, R.anim.mp02_up_gone);
      animDownAndVisible = AnimationUtils.loadAnimation(cxt, R.anim.mp02_down_visible);
      animDownAndGone = AnimationUtils.loadAnimation(cxt, R.anim.mp02_down_gone);

      animUpAndVisible2 = AnimationUtils.loadAnimation(cxt, R.anim.mp02_up_visible);
      animUpAndGone2 = AnimationUtils.loadAnimation(cxt, R.anim.mp02_up_gone);
      animDownAndVisible2 = AnimationUtils.loadAnimation(cxt, R.anim.mp02_down_visible);
      animDownAndGone2 = AnimationUtils.loadAnimation(cxt, R.anim.mp02_down_gone);
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
    item = LayoutInflater.from(layoutView.getContext()).inflate(R.layout.temp_layout_2, null);

    tempTitle01 = item.findViewById(R.id.temp_item_name0);
    tempTitle02 = item.findViewById(R.id.temp_item_name02);
    tempTitle1 = item.findViewById(R.id.temp_item_name);
    tempTitle2 = item.findViewById(R.id.temp_item_name2);
//        tempEdt = item.findViewById( R.id.temp_digits );
    tempTitle1.setTextSize(mFocusTextSize);
    tempTitle2.setTextSize(mFocusTextSize);

    if (layoutView instanceof FrameLayout) {
//            Log.e(TAG,"layoutView is FrameLayout");
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
      lp.setMargins(0, marginTop, 0, 0);
      layoutView.addView(item, lp);
      lp.height = mItemHeight + 32;
//            item.setLayoutParams( lp );
    } else {
//            Log.e(TAG,"layoutView is RelativeLayout");
      RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
      lp.setMargins(0, marginTop, 0, 0);
      lp.height = mItemHeight + 32;
//            item.setLayoutParams( lp );
      layoutView.addView(item, lp);
    }

//        Log.e(TAG,"layoutView is RelativeLayout " + item.isShown() + item.getWidth()+" height :"  +item.getHeight());
  }


  @Override
  public void upIn(String title01, String title02, String title1, String title2) {

    if (title01 != null && title01.equals(title1)) {
      title01 = " ";
    }

    tempTitle02.setText(" ");

    //tempTitle2.setFocusable(true);
    //tempTitle2.setFocusableInTouchMode(true);


    setOutVisibility(false);
    //setEdtVisibility(true);

    tempTitle01.setText(title01);
    tempTitle01.startAnimation(animUpAndGone2);
    animUpAndGone2.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        //tempTitle02.setText(title02);
        //tempTitle02.startAnimation(animUpAndVisible2);

        setOutVisibility(true);
        setItemVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
    tempTitle02.setText(title02);
    tempTitle02.startAnimation(animUpAndVisible2);

    tempTitle1.setText(title1);
    tempTitle1.startAnimation(animUpAndGone);
    animUpAndGone.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
        setOutVisibility(false);
      }

      @Override
      public void onAnimationEnd(Animation animation) {
        setOutVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
    tempTitle2.setText(title2);
    //tempTitle2.requestFocus();
    tempTitle2.startAnimation(animUpAndVisible);
  }


  @Override
  public void downIn(String title01, String title02, String title1, String title2) {

    if (title01.equals(title1)) {
      title01 = " ";
    }

    tempTitle02.setText(" ");

    setOutVisibility(false);
    //setEdtVisibility(true);

    tempTitle01.setText(title01);
    tempTitle01.startAnimation(animDownAndGone2);
    animDownAndGone2.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        //tempTitle02.setText(title02);
        //tempTitle02.startAnimation(animDownAndVisible2);
        setOutVisibility(true);
        setItemVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
    tempTitle02.setText(title02);
    tempTitle02.startAnimation(animDownAndVisible2);

    tempTitle1.setText(title1);
    tempTitle1.startAnimation(animDownAndGone);
    animDownAndGone.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
        setOutVisibility(false);
      }

      @Override
      public void onAnimationEnd(Animation animation) {

        setOutVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
    tempTitle2.setText(title2);
    tempTitle2.startAnimation(animDownAndVisible);
  }

  @Override
  public void downInWithEdt(String title1, String title2) {
    tempTitle1.setText(title1);
//        tempEdt.setText( title2 );
    setEdtVisibility(false);
//        tempEdt.setTextSize( mFocusTextSize );
//        tempEdt.startAnimation( animDownAndVisible );
    setOutVisibility(false);
    tempTitle1.startAnimation(animDownAndGone);
    animDownAndGone.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        setOutVisibility(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
  }

  @Override
  public void editTextChange(String txt) {
//        tempEdt.setText( txt );
  }

//    @Override
//    public void downOut(String title) {
//    }

  @Override
  public void setInVisibility(boolean isGone) {
    tempTitle02.setVisibility(isGone ? View.GONE : View.VISIBLE);
    tempTitle2.setVisibility(isGone ? View.GONE : View.VISIBLE);
  }

  @Override
  public void setOutVisibility(boolean isGone) {
    tempTitle01.setVisibility(isGone ? View.GONE : View.VISIBLE);
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
  public void ref() {
    //tempTitle1.setText(text);
    tempTitle2.requestFocus();
  }

  @Override
  public void setItemText(String text) {
    //tempTitle1.setText(text);
    tempTitle2.setText(text);
  }
}