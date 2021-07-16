package org.thoughtcrime.securesms.util.adapter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;

import java.util.Arrays;
import java.util.List;

public final class FixedViewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<View> viewList;

    private static final String TAG = "FixedViewsAdapter";

    private boolean hidden;
    public int mFocusHeight;
    public int mNormalHeight;
    public int mNormalPaddingX;
    public int mFocusPaddingX;
    public int mFocusTextSize;
    public int mNormalTextSize;
    private static String oldtext = "";
    private ItemAnimViewController mItemAnimController;
    private boolean isScorllUp = false;
    private View.OnFocusChangeListener onFocusChangeListener;

    public FixedViewsAdapter(@NonNull View... viewList) {
        this.viewList = Arrays.asList(viewList);
    }

    public FixedViewsAdapter(Context context, int mTop, RelativeLayout relativeLayout, @NonNull View... viewList) {
        this.viewList = Arrays.asList(viewList);
        updateLayoutInfo(context);
    }

    public FixedViewsAdapter(Context context, int mTop, RelativeLayout relativeLayout, View.OnFocusChangeListener listener, @NonNull View... viewList) {
        this.viewList = Arrays.asList(viewList);
        updateLayoutInfo(context);
        onFocusChangeListener = listener;
    }

    private void updateLayoutInfo(Context context) {
        Resources res = context.getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
    }

    @Override
    public int getItemCount() {
        return hidden ? 0 : viewList.size();
    }

    /**
     * @return View type is the index.
     */
    @Override
    public int getItemViewType(int position) {
        return position;
    }

    /**
     * @param viewType The index in the list of views.
     */
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        return new RecyclerView.ViewHolder(viewList.get(viewType)) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        View view = viewHolder.itemView;
        if (onFocusChangeListener != null) {
            view.setOnFocusChangeListener(onFocusChangeListener);
        } else {
            view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (isScorllUp) {

                    } else
                        startFocusAnimationSingleLine(v, hasFocus);
                }
            });
        }
    }

    private void startFocusAnimationSingleLine(View v, boolean focused) {

        ValueAnimator va;
        if (v instanceof TextView) {
            TextView item = (TextView) v;
            if (focused) {
                va = ValueAnimator.ofFloat(0, 1);
            } else {
                va = ValueAnimator.ofFloat(1, 0);
            }

            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float scale = (float) valueAnimator.getAnimatedValue();
                    float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
                    float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
                    float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
                    int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
                    int color = alpha * 0x1000000 + 0xffffff;


                    item.setPadding((int) padding, item.getPaddingTop(), item.getPaddingRight(), item.getPaddingBottom());
                    item.setTextSize((int) textsize);
                    item.setTextColor(color);
                    item.getLayoutParams().height = (int) height;

                }
            });

            FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
            va.setInterpolator(FastOutLinearInInterpolator);
            if (focused) {
                va.setDuration(270);
                va.start();
            } else {
                va.setDuration(270);
                va.start();
            }
        }
    }

    public void setScorllUp(boolean scorllUp) {
        isScorllUp = scorllUp;
    }

    private void startFocusAnimationSingleLine2(View v, boolean focused) {
        ValueAnimator va;
        TextView item = (TextView) v;
        //int position = (int) item.getTag();
        if (!focused) {

            oldtext = item.getText().toString();
        }
//    mItemAnimController.setItemVisibility(false);
//    if (focused) {
//      if (isScorllUp){
//        mItemAnimController.actionUpIn(oldtext, ((TextView)viewList.get(0)).getText().toString());
//      }else {
//        mItemAnimController.actionUpIn(oldtext, ((TextView)viewList.get(0)).getText().toString());
//      }
//    } else {
//      mItemAnimController.setItemVisibility(true);
//    }

        float height = ((float) (mFocusHeight - mNormalHeight)) * (focused ? 1 : 0) + (float) mNormalHeight;
        float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (focused ? 1 : 0) + mNormalTextSize;
        float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (focused ? 1 : 0);
        int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (focused ? 1 : 0));
        int color = alpha * 0x1000000 + 0xffffff;
        item.setTextSize((int) textsize);
        item.setTextColor(color);
        item.getLayoutParams().height = (int) height;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void hide() {
        setHidden(true);
    }

    public void show() {
        setHidden(false);
    }

    private void setHidden(boolean hidden) {
        if (this.hidden != hidden) {
            this.hidden = hidden;

            if (hidden) {
                notifyItemRangeRemoved(0, viewList.size());
            } else {
                notifyItemRangeInserted(0, viewList.size());
            }
        }
    }

    public void setFocusChangeListener(View.OnFocusChangeListener listener) {
        onFocusChangeListener = listener;
    }
}
