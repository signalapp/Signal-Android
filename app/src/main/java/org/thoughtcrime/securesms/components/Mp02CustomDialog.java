package org.thoughtcrime.securesms.components;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.KeyEvent;

import androidx.annotation.IdRes;

import org.thoughtcrime.securesms.R;

import java.lang.ref.WeakReference;

public class Mp02CustomDialog extends AlertDialog {
  public static final String TAG = "Mp02CustomDialog";

  private Context mContext;
  private LinearLayout mLinearLayoutTop;
  private LinearLayout mLinearLayoutBottom;
  private TextView mTvFocusBuffer[] = new TextView[2];
  private TextView mTvTopBuffer[] = new TextView[2];
  private TextView mTvBottomBuffer[] = new TextView[3];
  private Animation mAnimUpVisible;
  private Animation mAnimUpGone;
  private Animation mAnimDownVisible;
  private Animation mAnimDownGone;
  private ButtonHandler mHandler;

  private final View.OnClickListener mDialogClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View view) {
      int poi = (int) view.getTag();
      if (poi == 1) {
        mHandler.obtainMessage(ButtonHandler.MSG_DIALOG_POSITIVE).sendToTarget();
      } else if (poi == 2) {
        mHandler.obtainMessage(ButtonHandler.MSG_DIALOG_NEGATIVE).sendToTarget();
      }
      if(poi != 0){
        mHandler.obtainMessage(ButtonHandler.MSG_DISMISS_DIALOG).sendToTarget();
      }
    }
  };

  private static final class ButtonHandler extends Handler {
    private static final int MSG_DISMISS_DIALOG = -1;
    private static final int MSG_DIALOG_POSITIVE = 1;
    private static final int MSG_DIALOG_NEGATIVE = 2;

    private WeakReference<Mp02CustomDialog> mDialog;

    public ButtonHandler(Mp02CustomDialog dialog) {
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
          if (mDialog.get().mPositiveListener != null) {
            mDialog.get().mPositiveListener.onDialogKeyClicked();
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

  private String mMessage = "msg";
  private String mPositive = "positive";
  private String mNegative = "negative";
  private int mPoi = 0;

  public interface Mp02OnBackKeyListener {
    void onBackClicked();
  }

  public interface Mp02DialogKeyListener {
    void onDialogKeyClicked();
  }

  private Mp02OnBackKeyListener mBackListener;
  private Mp02DialogKeyListener mPositiveListener;
  private Mp02DialogKeyListener mNegativeListener;

  public Mp02CustomDialog(Context context) {
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
    setContentView(R.layout.mp02_custom_dialog);
    mLinearLayoutTop = findViewById(R.id.layout_top);
    mLinearLayoutBottom = findViewById(R.id.layout_bottom);
    //init Focus on main msg on dialog
    mPoi = 0;
    mTvFocusBuffer[0] = findViewById(R.id.focus_item_0);
    mTvFocusBuffer[1] = findViewById(R.id.focus_item_1);
    mTvFocusBuffer[0].setText(mMessage);
    mTvFocusBuffer[1].setText(mMessage);
    mTvFocusBuffer[1].setTag(mPoi);
    mTvFocusBuffer[1].setOnClickListener(mDialogClickListener);
    //init Top layout
    mTvTopBuffer[0] = findViewById(R.id.unfocus_top_0);
    mTvTopBuffer[1] = findViewById(R.id.unfocus_top_1);
    mTvTopBuffer[0].setText(mMessage);
    mTvTopBuffer[1].setText(mPositive);
    //init Bottom layout
    mTvBottomBuffer[0] = findViewById(R.id.unfocus_bottom_0);
    mTvBottomBuffer[1] = findViewById(R.id.unfocus_bottom_1);
    mTvBottomBuffer[2] = findViewById(R.id.unfocus_bottom_2);
    mTvBottomBuffer[0].setText(mPositive);
    mTvBottomBuffer[1].setText(mNegative);
    mTvBottomBuffer[1].setText(mNegative);


    mTvFocusBuffer[0].setVisibility(View.GONE);
    mTvFocusBuffer[1].setVisibility(View.VISIBLE);
    mLinearLayoutTop.removeAllViews();
    mLinearLayoutBottom.removeAllViews();
    mLinearLayoutBottom.addView(mTvBottomBuffer[0]);
    mLinearLayoutBottom.addView(mTvBottomBuffer[1]);
    mTvFocusBuffer[1].setTextSize(TypedValue.COMPLEX_UNIT_PX, mPoi == 0 ? 24 : 40);
    //init Animation
    mAnimUpVisible = AnimationUtils.loadAnimation(mContext, R.anim.mp02_up_visible);
    mAnimUpGone = AnimationUtils.loadAnimation(mContext, R.anim.mp02_up_gone);
    mAnimDownVisible = AnimationUtils.loadAnimation(mContext, R.anim.mp02_down_visible);
    mAnimDownGone = AnimationUtils.loadAnimation(mContext, R.anim.mp02_down_gone);
  }

  @Override
  public boolean onKeyDown(int KeyCode, KeyEvent ev) {

    switch (KeyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
        if (mPoi == 0) {
          break;
        }
        mTvFocusBuffer[0].setText(getTextByPoi(mPoi));
        mTvFocusBuffer[0].setVisibility(View.GONE);
        mTvFocusBuffer[0].startAnimation(mAnimDownGone);
        mTvFocusBuffer[1].setText(getTextByPoi(--mPoi));
        mTvFocusBuffer[1].setTextSize(TypedValue.COMPLEX_UNIT_PX, mPoi == 0 ? 24 : 40);
        mTvFocusBuffer[1].setVisibility(View.VISIBLE);
        mTvFocusBuffer[1].startAnimation(mAnimDownVisible);
        mLinearLayoutTop.getChildAt(mPoi).startAnimation(mAnimDownGone);
        mLinearLayoutTop.removeViewAt(mPoi);
        mLinearLayoutBottom.addView(mTvBottomBuffer[mPoi], 0);
        mLinearLayoutBottom.getChildAt(0).startAnimation(mAnimDownVisible);
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        if (mPoi == 2) {
          break;
        }
        mTvFocusBuffer[0].setText(getTextByPoi(mPoi));
        mTvFocusBuffer[0].setVisibility(View.GONE);
        mTvFocusBuffer[0].startAnimation(mAnimUpGone);
        mTvFocusBuffer[1].setText(getTextByPoi(++mPoi));
        mTvFocusBuffer[1].setTextSize(TypedValue.COMPLEX_UNIT_PX, mPoi == 0 ? 24 : 40);
        mTvFocusBuffer[1].setVisibility(View.VISIBLE);
        mTvFocusBuffer[1].startAnimation(mAnimUpVisible);
        mLinearLayoutTop.addView(mTvTopBuffer[mPoi - 1], mPoi - 1);
        mLinearLayoutTop.getChildAt(mPoi - 1).startAnimation(mAnimUpVisible);
        mLinearLayoutBottom.getChildAt(0).startAnimation(mAnimUpGone);
        mLinearLayoutBottom.removeViewAt(0);
        break;
      case KeyEvent.KEYCODE_BACK:
        if (mBackListener != null) {
          mBackListener.onBackClicked();
        }
        this.dismiss();
    }
    mTvFocusBuffer[1].setTag(mPoi);
    return super.onKeyDown(KeyCode, ev);
  }

  private String getTextByPoi(int poi) {
    switch (poi) {
      default:
      case 0:
        return mMessage;
      case 1:
        return mPositive;
      case 2:
        return mNegative;
    }
  }

  public void setMessage(String msg) {
    mMessage = msg;
  }

  public void setPositiveListener(int resId, Mp02DialogKeyListener listener) {
    mPositive = mContext.getString(resId);
    mPositiveListener = listener;
  }

  public void setNegativeListener(int resId, Mp02DialogKeyListener listener) {
    mNegative = mContext.getString(resId);
    mNegativeListener = listener;
  }

  public void setBackKeyListener(Mp02OnBackKeyListener listener) {
    mBackListener = listener;
  }
}
