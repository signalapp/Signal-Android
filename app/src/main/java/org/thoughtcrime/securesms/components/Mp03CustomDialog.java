package org.thoughtcrime.securesms.components;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;

import android.widget.TextView;

import androidx.interpolator.view.animation.FastOutLinearInInterpolator;

import org.thoughtcrime.securesms.R;

import java.lang.ref.WeakReference;

public class Mp03CustomDialog extends AlertDialog {
    public static final String TAG = "Mp03CustomDialog";

    private Context mContext;
    private TextView mReminderDisableTitle;
    private TextView mReminderDisableDescription;
    private TextView mReminderDisableCancel;
    private TextView mReminderDisableTurnOff;
    private EditText mReminderDisablePin;
    private Animation mAnimUpVisible;
    private Animation mAnimUpGone;
    private Animation mAnimDownVisible;
    private Animation mAnimDownGone;
    private ButtonHandler mHandler;
    private int mNormalPadding = 30;
    private int mFocusedPadding = 5;
    private int mNormalTextSize = 24;
    private int mFocusedTextSize = 40;
    private int mNormalHeight = 32;
    private int mFocusedHeight = 56;

    private final View.OnClickListener mDialogClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            if(view == mReminderDisableCancel){
                mHandler.obtainMessage(ButtonHandler.MSG_DIALOG_NEGATIVE).sendToTarget();
            } else if (view == mReminderDisableTurnOff){
                mHandler.obtainMessage(ButtonHandler.MSG_DIALOG_POSITIVE).sendToTarget();
            }
        }
    };

    private static final class ButtonHandler extends Handler {
        private static final int MSG_DISMISS_DIALOG = -1;
        private static final int MSG_DIALOG_POSITIVE = 1;
        private static final int MSG_DIALOG_NEGATIVE = 2;

        private WeakReference<Mp03CustomDialog> mDialog;

        public ButtonHandler(Mp03CustomDialog dialog) {
            mDialog = new WeakReference<>(dialog);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                default:
                case MSG_DISMISS_DIALOG:
                    mDialog.get().dismiss();
                    break;
                case MSG_DIALOG_POSITIVE:
                    int result = 0;
                    if (mDialog.get().mPositiveListener != null) {
                        result = mDialog.get().mPositiveListener.onDialogKeyClicked();
                    }
                    if(result == 1) {
                        mDialog.get().dismiss();
                    }
                    break;
                case MSG_DIALOG_NEGATIVE:
                    if (mDialog.get().mNegativeListener != null) {
                        mDialog.get().mNegativeListener.onDialogKeyClicked();
                    }
                    mDialog.get().dismiss();
                    break;
            }
        }
    }

    private String mTitle = "title";
    private String mMessage = "msg";
    private String mPositive = "positive";
    private String mNegative = "negative";

    public interface Mp03OnBackKeyListener {
        void onBackClicked();
    }

    public interface Mp03DialogKeyListener {
        int onDialogKeyClicked();
    }

    private Mp03OnBackKeyListener mBackListener;
    private Mp03DialogKeyListener mPositiveListener;
    private Mp03DialogKeyListener mNegativeListener;

    public Mp03CustomDialog(Context context) {
        super(context, R.style.Mp02_Signal_CustomDialog);
        this.mContext = context;
        mHandler = new ButtonHandler(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(layoutParams);
        setupDialog();
    }


    private void setupDialog() {
        setContentView(R.layout.mp03_custom_dialog);

        mReminderDisableTitle = findViewById(R.id.reminder_disable_title);
        mReminderDisableDescription = findViewById(R.id.reminder_disable_description);
        mReminderDisableTitle.setText(mTitle);
        mReminderDisableDescription.setText(mMessage);
        mReminderDisablePin = findViewById(R.id.reminder_disable_pin_0);
        mReminderDisablePin.setOnClickListener(mDialogClickListener);
        mReminderDisableCancel = findViewById(R.id.reminder_disable_cancel);
        mReminderDisableTurnOff = findViewById(R.id.reminder_disable_turn_off);
        mReminderDisableCancel.setText(mNegative);
        mReminderDisableTurnOff.setText(mPositive);
        mReminderDisableCancel.setOnClickListener(mDialogClickListener);
        mReminderDisableTurnOff.setOnClickListener(mDialogClickListener);
        mReminderDisableTurnOff.setOnFocusChangeListener(new pinEdithanageListener());
        mReminderDisableCancel.setOnFocusChangeListener(new cancelButtonChanageListener());
        mReminderDisableTurnOff.setOnFocusChangeListener(new turnoffButtonChanageListener());
        //init Animation
        mAnimUpVisible = AnimationUtils.loadAnimation(mContext, R.anim.mp02_up_visible);
        mAnimUpGone = AnimationUtils.loadAnimation(mContext, R.anim.mp02_up_gone);
        mAnimDownVisible = AnimationUtils.loadAnimation(mContext, R.anim.mp02_down_visible);
        mAnimDownGone = AnimationUtils.loadAnimation(mContext, R.anim.mp02_down_gone);
    }

    public void setTitle(String title) {
        mTitle = title;
    }


    public void setMessage(String msg) {
        mMessage = msg;
    }

    public void setPositiveListener(int resId, Mp03DialogKeyListener listener) {
        mPositive = mContext.getString(resId);
        mPositiveListener = listener;
    }

    public void setNegativeListener(int resId, Mp03DialogKeyListener listener) {
        mNegative = mContext.getString(resId);
        mNegativeListener = listener;
    }

    public void setBackKeyListener(Mp03OnBackKeyListener listener) {
        mBackListener = listener;
    }

    private class cancelButtonChanageListener implements View.OnFocusChangeListener {
        @Override
        public void onFocusChange(View view,boolean hasFocus){
            if(view == mReminderDisableCancel) {
                if (mReminderDisableCancel.isFocused()) {
                    mReminderDisableCancel.setPadding(5, 0, 0, 0);
                    mReminderDisableCancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, 40);
                    //mReminderDisableCancel.startAnimation(mAnimDownVisible);
                    startFocusAnimation(mReminderDisableCancel, true);
                } else {
                    mReminderDisableCancel.setPadding(30, 0, 0, 0);
                    mReminderDisableCancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
                    startFocusAnimation(mReminderDisableCancel, false);
                }
            }
        }
    }

    private class turnoffButtonChanageListener implements View.OnFocusChangeListener {
        @Override
        public void onFocusChange(View view,boolean hasFocus){
            if(view == mReminderDisableTurnOff) {
                if (mReminderDisableTurnOff.isFocused()) {
                    mReminderDisableTurnOff.setPadding(5, 0, 0, 0);
                    mReminderDisableTurnOff.setTextSize(TypedValue.COMPLEX_UNIT_PX, 40);
                    //mReminderDisableTurnOff.startAnimation(mAnimUpVisible);
                    startFocusAnimation(mReminderDisableTurnOff, true);
                } else {
                    mReminderDisableTurnOff.setPadding(30, 0, 0, 0);
                    mReminderDisableTurnOff.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
                    //mReminderDisableTurnOff.startAnimation(mAnimUpGone);
                    startFocusAnimation(mReminderDisableTurnOff, false);
                }
            }
        }
    }

    private class pinEdithanageListener implements View.OnFocusChangeListener {
        @Override
        public void onFocusChange(View view,boolean hasFocus){
            if(view == mReminderDisablePin) {
                if (mReminderDisablePin.isFocused()) {
                    mReminderDisablePin.setPadding(5, 0, 0, 0);
                    mReminderDisablePin.setTextSize(TypedValue.COMPLEX_UNIT_PX, 40);
                    //mReminderDisablePin.startAnimation(mAnimDownVisible);
                } else {
                    mReminderDisablePin.setPadding(30, 0, 0, 0);
                    mReminderDisablePin.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
                    //mReminderDisablePin.startAnimation(mAnimDownGone);
                }
            }
        }
    }

    private void startFocusAnimation(TextView tv, boolean focused) {

        ValueAnimator va;
        if (focused) {
            va = ValueAnimator.ofFloat(0, 1);
        } else {
            va = ValueAnimator.ofFloat(1, 0);
        }

        va.addUpdateListener(valueAnimator -> {
            float scale = (float) valueAnimator.getAnimatedValue();
            float height = ((float) (mFocusedHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
            float textsize = ((float) (mFocusedTextSize - mNormalTextSize)) * (scale) + (float) mNormalTextSize;
            float padding = (float) mNormalPadding - ((float) (mNormalPadding - mFocusedPadding)) * (scale);
            int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
            int color = alpha * 0x1000000 + 0xffffff;

            tv.setTextColor(color);
            tv.setPadding((int) padding, tv.getPaddingTop(), tv.getPaddingRight(), tv.getPaddingBottom());
            tv.setTextSize((int) textsize);
            tv.getLayoutParams().height = (int) height;
        });

        FastOutLinearInInterpolator mInterpolator = new FastOutLinearInInterpolator();
        va.setInterpolator(mInterpolator);
        if (focused) {
            va.setDuration(300);
            va.start();
        } else {
            va.setDuration(300);
            va.start();
        }
        tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
    }
}