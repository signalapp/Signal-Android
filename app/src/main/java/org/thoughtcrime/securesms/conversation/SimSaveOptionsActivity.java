package org.thoughtcrime.securesms.conversation;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import org.thoughtcrime.securesms.R;

public class SimSaveOptionsActivity extends Activity {

    private final static String TAG = "SimSaveOptionsActivity";
    public final static int ITEM_NEW_CONTACT = 0;
    public final static int ITEM_ADD_TO_EXISTING = 1;
    private static final int ADD_CONTACT = 8;

    private RecyclerView mRecyclerView;
    private SimBaseOptionsAdapter mAdaper;
    private int mNormalHeight;
    private int mFocusHeight;
    private int mNormalPaddingX;
    private int mFocusPaddingX;
    private int mNormalTextSize;
    private int mFocusTextSize;

    private Intent mIntentAdd;
    private Intent mIntentExist;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.sim_save_options_activity);

        Intent intent = getIntent();
        mIntentAdd = (Intent)intent.getParcelableExtra("intentAdd");
        mIntentExist = (Intent)intent.getParcelableExtra("intentExist");

        Resources res = getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

        List<String> dialtactsOptions = getDatas();

        mRecyclerView = (RecyclerView) findViewById(R.id.sim_save_options);
        LinearLayoutManager layoutmanager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutmanager);
        mAdaper = new SimBaseOptionsAdapter(this, dialtactsOptions, mOnFocusChangeHandler,mOnClickListener);
        mRecyclerView.setAdapter(mAdaper);
        mRecyclerView.setHasFixedSize(true);
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            Object tag = v.getTag();
            int position = (int)tag;

            if (position == ITEM_NEW_CONTACT) {
                startActivityForResult(mIntentAdd, ADD_CONTACT);
            } else if (position == ITEM_ADD_TO_EXISTING) {
                startActivityForResult(mIntentExist, ADD_CONTACT);
            }
        }
    };

    public void onActivityResult(final int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);
        if (reqCode == ADD_CONTACT) {
            setResult(resultCode, data);
        }
    }

    private final View.OnFocusChangeListener mOnFocusChangeHandler =
            new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    startFocusAnimation(v,hasFocus);
                }
            };
    public void startFocusAnimation(View v,boolean focused){
        ValueAnimator va ;
        TextView item = (TextView)v;
        String text = (item!=null)? item.getText().toString() : null;
        if(focused){
            va = ValueAnimator.ofFloat(0,1);
        }else{
            va = ValueAnimator.ofFloat(1,0);
        }
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float scale = (float)valueAnimator.getAnimatedValue();
                float height = ((float)(mFocusHeight - mNormalHeight))*(scale)+(float)mNormalHeight;
                float textsize = ((float)(mFocusTextSize - mNormalTextSize))*(scale) + mNormalTextSize;
                float padding = (float)mNormalPaddingX -((float)(mNormalPaddingX - mFocusPaddingX))*(scale);
                int alpha = (int)((float)0x81 + (float)((0xff - 0x81))*(scale));
                int color =  alpha*0x1000000 + 0xffffff;
                item.setTextSize((int)textsize);
                item.setTextColor(color);
                item.setPadding(
                        (int)padding, item.getPaddingTop(),
                        item.getPaddingRight(), item.getPaddingBottom());
                item.getLayoutParams().height = (int)height;
                if(focused){
                    item.setSingleLine(false);
                    item.setMaxLines(2);
                    item.setLines(2);
                }else{
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

    private List<String> getDatas() {
        List<String> datas = Arrays.asList(getResources().getStringArray(R.array.sim_save_options));
        return datas;
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        finish();
    }
}