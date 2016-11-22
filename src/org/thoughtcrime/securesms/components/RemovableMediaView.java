package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class RemovableMediaView extends FrameLayout {

    private final @NonNull ImageView remove;
    private final int removeSize;

    private @Nullable View current;

    public RemovableMediaView(Context context) {
        this(context, null);
    }

    public RemovableMediaView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RemovableMediaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.remove     = (ImageView)LayoutInflater.from(context).inflate(R.layout.media_view_remove_button, this, false);
        this.removeSize = getResources().getDimensionPixelSize(R.dimen.media_bubble_remove_button_size);

        this.remove.setVisibility(View.GONE);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        this.addView(remove);
    }

    public void display(@Nullable View view) {
        if (view == current) return;
        if (current != null) current.setVisibility(View.GONE);

        if (view != null) {
            MarginLayoutParams params = (MarginLayoutParams)view.getLayoutParams();
            params.setMargins(0, removeSize / 2, removeSize / 2, 0);
            view.setLayoutParams(params);

            view.setVisibility(View.VISIBLE);
            remove.setVisibility(View.VISIBLE);
        } else {
            remove.setVisibility(View.GONE);
        }

        current = view;
    }

    public void setRemoveClickListener(View.OnClickListener listener) {
        this.remove.setOnClickListener(listener);
    }
}