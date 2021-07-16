package org.thoughtcrime.securesms.preferences.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceViewHolder;

import org.thoughtcrime.securesms.R;

/**
 * Check box preference with check box replaced by radio button.
 * <p>
 * Functionally speaking, it's actually a CheckBoxPreference. We only modified
 * the widget to RadioButton to make it "look like" a RadioButtonPreference.
 * <p>
 * In other words, there's no "RadioButtonPreferenceGroup" in this
 * implementation. When you check one RadioButtonPreference, if you want to
 * uncheck all the other preferences, you should do that by code yourself.
 */
public class MpRadioButtonPreference extends CheckBoxPreference {
    protected int mFocusHeight;
    protected int mNormalHeight;
    protected int mNormalPaddingX;
    protected int mFocusPaddingX;
    protected int mFocusTextSize;
    protected int mNormalTextSize;
    protected int mFocusedColor;
    protected int mNormalColor;
    protected Context mContext;
    protected TextView TX;
    protected int foc = 0;
    protected int foc1;
    protected int foctemp;
    private MpRadioButtonPreference.OnClickListener mListener = null;

    private static final String PREFERENCE_EMPTY = "empty";

    public interface OnClickListener {
        void onRadioButtonClicked(MpRadioButtonPreference emiter);
    }

    public MpRadioButtonPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        //setWidgetLayoutResource(R.layout.preference_widget_radiobutton);
        mContext = context;
    }

    public MpRadioButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public MpRadioButtonPreference(Context context) {
        this(context, null);
    }

    public void setOnClickListener(MpRadioButtonPreference.OnClickListener listener) {
        mListener = listener;
    }

    @Override
    public void onClick() {
        if (mListener != null) {
            mListener.onRadioButtonClicked(this);
        }
    }

    protected void setLayout() {
        setLayoutResource(R.layout.mp_radiobutton_layout);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        int viewId = view.itemView.getId();
        super.onBindViewHolder(view);

        if (PREFERENCE_EMPTY.equals(getKey())) {
            view.itemView.findViewById(R.id.textview).setVisibility(View.GONE);
            view.itemView.findViewById(R.id.check_layout).setVisibility(View.GONE);
            view.itemView.setFocusable(false);
            view.itemView.setFocusableInTouchMode(false);
            return;
        }

        view.itemView.setId(viewId);
        bindView(view);

        if (foc != 0) {
            view.itemView.setId(foc);
            foctemp = foc;
            view.itemView.requestFocus();
            TX = view.itemView.findViewById(R.id.textview);
//	    TX.setFocusable(true);
//	    TX.requestFocus();
            startFocusAnimation2(TX, true);
            foc = 0;
            foc1 = 100;
        }
    }

    private void init() {
        setLayout();
        Resources res = mContext.getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
        mFocusedColor = res.getColor(R.color.focused_text_color);
        mNormalColor = res.getColor(R.color.normal_text_color);
    }

    protected void bindView(PreferenceViewHolder holder) {
        TextView titleView = holder.itemView.findViewById(R.id.textview);
        if (titleView != null) {
            final CharSequence title = getTitle();
            if (!TextUtils.isEmpty(title)) {
                titleView.setText(title);
            }
        }
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            TextView titleView1 = v.findViewById(R.id.textview);
            View mCheckLayout = v.findViewById(R.id.check_layout);
            if (foc1 == 100) {
                holder.itemView.setId(foctemp);
                foc1 = 101;
                startFocusAnimation(v, false);

            }
            if (titleView1 != null) {
                startFocusAnimation(v, hasFocus);
            }
            if (hasFocus) {
                mCheckLayout.setPadding(mCheckLayout.getPaddingLeft(),
                        mCheckLayout.getPaddingTop(),
                        mCheckLayout.getPaddingRight(), 11);
            } else {
                mCheckLayout.setPadding(mCheckLayout.getPaddingLeft(),
                        mCheckLayout.getPaddingTop(),
                        mCheckLayout.getPaddingRight(), 6);
            }
        });
    }

    public void initfocus(int index) {
        foc = index;
    }

    public void startFocusAnimation(View v, boolean focused) {
        ValueAnimator va;
        final TextView item = v.findViewById(R.id.textview);
        if (focused) {
            va = ValueAnimator.ofFloat(0, 1);
            item.setSelected(true);
            item.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        } else {
            item.setEllipsize(TextUtils.TruncateAt.END);
            va = ValueAnimator.ofFloat(1, 0);
        }
        va.addUpdateListener(valueAnimator -> {
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
            v.getLayoutParams().height = (int) height;
        });

        FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
        va.setInterpolator(FastOutLinearInInterpolator);
        va.setDuration(270);
        va.start();
    }

    public void startFocusAnimation2(View v, boolean focused) {
        ValueAnimator va;
        final TextView item = (TextView) v;
        if (focused) {
            va = ValueAnimator.ofFloat(0, 1);
            item.setSelected(true);
            item.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        } else {
            item.setEllipsize(TextUtils.TruncateAt.END);
            va = ValueAnimator.ofFloat(1, 0);
        }
        va.addUpdateListener(valueAnimator -> {
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
            v.getLayoutParams().height = (int) height;
        });

        FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
        va.setInterpolator(FastOutLinearInInterpolator);
        va.setDuration(270);
        va.start();
    }
}