package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WebRtcCallMenuText extends androidx.appcompat.widget.AppCompatTextView implements View.OnClickListener {
    private OnTextChangedListener textChangedListener;
    private boolean isChecked;

    public WebRtcCallMenuText(Context context) {
        super(context);
    }

    public WebRtcCallMenuText(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WebRtcCallMenuText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnTextChangedListener(@NonNull OnTextChangedListener listener) {
        this.textChangedListener = listener;
        setOnClickListener(this);
    }

    private void notifyListener(boolean on) {
        if (textChangedListener == null) return;
        textChangedListener.textChanged(on);
    }

    public void setChecked(boolean checked) {
        if (checked != isChecked) {
            isChecked = checked;
            notifyListener(isChecked);
        }
    }

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void onClick(View view) {
        if (isChecked) {
            isChecked = false;
        } else {
            isChecked = true;
        }
        notifyListener(isChecked);
    }
}
