package org.thoughtcrime.securesms;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class InviteEditText extends EditText {
    public InviteEditText(Context context) {
        super(context);
    }

    public InviteEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InviteEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public InviteEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (!isCursorVisible()){
            setSelection(getText().length());
        }
    }

}
