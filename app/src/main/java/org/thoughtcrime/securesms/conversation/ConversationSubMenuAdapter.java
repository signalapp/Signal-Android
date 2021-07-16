package org.thoughtcrime.securesms.conversation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.List;

public class ConversationSubMenuAdapter extends RecyclerView.Adapter<ConversationSubMenuAdapter.ViewHolder> {
    private int mNormalHeight;
    private int mFocusHeight;
    private int mNormalPaddingX;
    private int mFocusPaddingX;
    private int mNormalTextSize;
    private int mFocusTextSize;
    private List<String> data;
    LayoutInflater inflater;
    ItemClickListener clickListener;

    public ConversationSubMenuAdapter(Context context,ItemClickListener clickListener,List<String> data) {
        this.inflater = LayoutInflater.from(context);
        this.clickListener = clickListener;
        this.data = data;
        Resources res = context.getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.conversation_sub_menu_item, parent, false);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) clickListener.onItemClick((TextView) v);
            }
        });
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                startFocusAnimation(v, hasFocus);
            }
        });
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tv.setText(data.get(position));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    interface ItemClickListener {
        void onItemClick(TextView tv);
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
                float scale = (float) valueAnimator.getAnimatedValue();
                float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
                float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
                float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
                int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
                int color = alpha * 0x1000000 + 0xffffff;
                item.setTextSize((int) textsize);
                item.setTextColor(color);
                item.setPadding(
                        (int) padding, item.getPaddingTop(),
                        item.getPaddingRight(), item.getPaddingBottom());
                item.getLayoutParams().height = (int) height;
                if (focused) {
                    item.setSingleLine(false);
                    item.setMaxLines(2);
                    item.setLines(2);
                } else {
                    item.setSingleLine(true);
                    item.setMaxLines(1);
                    item.setLines(1);
                }
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tv;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.sub_menu);
        }
    }
}
