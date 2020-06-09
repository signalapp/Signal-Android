package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.DarkSearchView;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class EmojiSearchView extends DarkSearchView {

    public EmojiSearchView(@NonNull Context context) {
        this(context, null);
    }

    public EmojiSearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.search_view_style);
    }

    public EmojiSearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        EditText searchText = findViewById(androidx.appcompat.R.id.search_src_text);
        if (!TextSecurePreferences.isSystemEmojiPreferred(getContext())) {
            EmojiFilter.appendEmojiFilter(searchText);
        }
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        if (drawable instanceof EmojiDrawable) invalidate();
        else                                   super.invalidateDrawable(drawable);
    }
}
