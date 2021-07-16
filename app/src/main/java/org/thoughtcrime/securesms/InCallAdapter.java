package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import android.animation.ValueAnimator;

import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.RecyclerView;

public class InCallAdapter extends RecyclerView.Adapter<InCallAdapter.ViewHolder> {
  private Context mContext;
  private List<String> mList;
  private static final int HEADER_VIEW_TYPE = 1;
  private static final int CALL_OPTIONS_VIEW_TYPE = 2;
  private View.OnClickListener mClickListener;

  static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(View view) {
      super(view);
    }
  }

  public InCallAdapter(Context mContext, View.OnClickListener clickListener) {
    this.mContext = mContext;
    this.mClickListener = clickListener;
  }

    /*@Override
    public long getItemId(int position) {
        return position;
    }*/

  public void setlist(List<String> list) {
    this.mList = list;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    switch (viewType) {
      case HEADER_VIEW_TYPE:
//                    ViewHolder holder = new ViewHolder(mText);
//                    holder.setOnClickListener(mClickListener);
//                    holder.setOnFocusChangeListener(mOnFocusChangeListener);
//                    return null;
      case CALL_OPTIONS_VIEW_TYPE:
        TextView mText = (TextView) LayoutInflater.from(parent.getContext()).inflate(
                R.layout.sim_textview_item, parent, false);
        mText.setOnClickListener(mClickListener);
        mText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
          @Override
          public void onFocusChange(View v, boolean hasFocus) {
            startFocusAnimation(v, hasFocus);
          }
        });
        ViewHolder holder1 = new ViewHolder(mText);
        return holder1;
      default:
        return null;
    }
  }


  public void startFocusAnimation(View v, boolean focused) {
    ValueAnimator va;
    TextView item = (TextView) v;
    String text = (item != null) ? item.getText().toString() : null;
    if (focused) {
      va = ValueAnimator.ofFloat(0, 1);
    } else {
      va = ValueAnimator.ofFloat(1, 0);
    }
    va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator valueAnimator) {
//                float scale = (float)valueAnimator.getAnimatedValue();
//                float height = ((float)(mFocusHeight - mNormalHeight))*(scale)+(float)mNormalHeight;
//                float textsize = ((float)(mFocusTextSize - mNormalTextSize))*(scale) + mNormalTextSize;
//                float padding = (float)mNormalPaddingX -((float)(mNormalPaddingX - mFocusPaddingX))*(scale);
//                int alpha = (int)((float)0x81 + (float)((0xff - 0x81))*(scale));
//                int color =  alpha*0x1000000 + 0xffffff;
//                item.setTextSize((int)textsize);
//                item.setTextColor(color);
//                item.setPadding(
//                    (int)padding, item.getPaddingTop(),
//                    item.getPaddingRight(), item.getPaddingBottom());
//                item.getLayoutParams().height = (int)height;
      }
    });

    FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
    va.setInterpolator(FastOutLinearInInterpolator);
    va.setDuration(270);
    va.start();
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (position > 0) {
      TextView itemText = (TextView) holder.itemView;
      String Text = mList.get(position - 1);
      itemText.setText(Text);
      itemText.setTag(position - 1);
    } else {
      View itemView = holder.itemView;
      itemView.setTag(position - 1);
    }
  }

  @Override
  public int getItemCount() {
    return mList.size() + 1;
  }

  @Override
  public int getItemViewType(int position) {
    if (position == 0) { // Header
      return HEADER_VIEW_TYPE;
    } else {
      return CALL_OPTIONS_VIEW_TYPE;
    }
  }
}
