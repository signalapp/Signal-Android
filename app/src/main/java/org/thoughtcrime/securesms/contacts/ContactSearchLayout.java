package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * just fix the edittext not in fisrt item
 */
public class ContactSearchLayout extends LinearLayout {
    private static final String TAG = ContactSearchLayout.class.getSimpleName();
    private boolean mIsFirstAttached = false;
    private boolean isSelfFocus = true;
    public ContactSearchLayout(Context context) {
        super(context);
    }

    public ContactSearchLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ContactSearchLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        Log.d(TAG, "ContactSearchLayout : onFocusChanged="+gainFocus + " mIsFirstAttached="+mIsFirstAttached + "direction: "+direction);

        if (mIsFirstAttached){
            mIsFirstAttached = false;
        }else {
            if (gainFocus){
                    if (isSelfFocus){
                        clearFocus();
                        getChildAt(0).setFocusable(true);
                        getChildAt(0).setFocusableInTouchMode(true);
                        getChildAt(0).requestFocus();
                    }

            }else {
                    getChildAt(0).setFocusable(false);
                    getChildAt(0).setFocusableInTouchMode(false);
                    getChildAt(0).clearFocus();
            }
        }

        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {

        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIsFirstAttached = true;
        Log.d(TAG,"onFinishInflate");
    }


    ViewTreeObserver.OnGlobalFocusChangeListener onGlobalFocusChangeListener;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onGlobalFocusChangeListener = new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View view, View view1) {
                if (hasFocus()){
                    isSelfFocus = true;
                    Log.d(TAG,"onAttachedToWindow true");
                }else {
                    Log.d(TAG,"onAttachedToWindow false");
                    isSelfFocus = false;
                }
            }
        };

        getViewTreeObserver().addOnGlobalFocusChangeListener(onGlobalFocusChangeListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalFocusChangeListener(onGlobalFocusChangeListener);
    }
}
