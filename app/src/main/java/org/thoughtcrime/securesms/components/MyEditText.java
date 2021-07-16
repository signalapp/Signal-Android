package org.thoughtcrime.securesms.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

@SuppressLint("AppCompatCustomView")
public class MyEditText extends EditText implements TextWatcher, View.OnKeyListener {

    private   OnFilterChangedListener listener;
    private boolean noChange =false;

    public MyEditText(Context context) {
        super(context);
        initEdit();
    }

    public MyEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initEdit();
    }

    public MyEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initEdit();
    }

    private void initEdit() {
        addTextChangedListener(this);
        setOnKeyListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        noChange = true;
       notifyListener();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }


    public interface OnFilterChangedListener {
        void onFilterChanged(String filter,boolean nochange);
    }

    public void setOnFilterChangedListener(OnFilterChangedListener listener) {
        this.listener = listener;
    }

    private void notifyListener() {
        if (getText().toString().equals(" ")){
        }
        if (listener != null) listener.onFilterChanged(this.getText().toString(),noChange);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }
}
