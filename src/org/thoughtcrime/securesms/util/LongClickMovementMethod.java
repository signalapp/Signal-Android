package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

public class LongClickMovementMethod extends LinkMovementMethod {
    @SuppressLint("StaticFieldLeak")
    private static LongClickMovementMethod sInstance;

    private final GestureDetector gestureDetector;
    private View widget;
    private LongClickSpan currentSpan;

    private LongClickMovementMethod(final Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                if (currentSpan != null && widget != null) {
                    currentSpan.onLongClick(widget);
                    widget = null;
                    currentSpan = null;
                }
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (currentSpan != null && widget != null) {
                    currentSpan.onClick(widget);
                    widget = null;
                    currentSpan = null;
                }
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);


            LongClickSpan longClickSpan[] = buffer.getSpans(off, off, LongClickSpan.class);
            if (longClickSpan.length != 0) {
                LongClickSpan aSingleSpan = longClickSpan[0];
                Selection.setSelection(buffer, buffer.getSpanStart(aSingleSpan),
                        buffer.getSpanEnd(aSingleSpan));
                this.currentSpan = aSingleSpan;
                this.widget = widget;
                return gestureDetector.onTouchEvent(event);
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    public static LongClickMovementMethod getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LongClickMovementMethod(context.getApplicationContext());
        }
        return sInstance;
    }
}