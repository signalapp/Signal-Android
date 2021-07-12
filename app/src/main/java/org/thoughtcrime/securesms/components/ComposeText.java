package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.os.BuildCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.session.libsignal.utilities.Log;
import org.session.libsession.utilities.TextSecurePreferences;

public class ComposeText extends EmojiEditText {

    private CharSequence    hint;
    private SpannableString subHint;

    @Nullable private InputPanel.MediaListener      mediaListener;
    @Nullable private CursorPositionChangedListener cursorPositionChangedListener;

    public ComposeText(Context context) {
        super(context);
        initialize();
    }

    public ComposeText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ComposeText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public String getTextTrimmed(){
        return getText().toString().trim();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (!TextUtils.isEmpty(hint)) {
            if (!TextUtils.isEmpty(subHint)) {
                setHint(new SpannableStringBuilder().append(ellipsizeToWidth(hint))
                        .append("\n")
                        .append(ellipsizeToWidth(subHint)));
            } else {
                setHint(ellipsizeToWidth(hint));
            }
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);

        if (cursorPositionChangedListener != null) {
            cursorPositionChangedListener.onCursorPositionChanged(selStart, selEnd);
        }
    }

    private CharSequence ellipsizeToWidth(CharSequence text) {
        return TextUtils.ellipsize(text,
                getPaint(),
                getWidth() - getPaddingLeft() - getPaddingRight(),
                TruncateAt.END);
    }

    public void setHint(@NonNull String hint, @Nullable CharSequence subHint) {
        this.hint = hint;

        if (subHint != null) {
            this.subHint = new SpannableString(subHint);
            this.subHint.setSpan(new RelativeSizeSpan(0.5f), 0, subHint.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        } else {
            this.subHint = null;
        }

        if (this.subHint != null) {
            super.setHint(new SpannableStringBuilder().append(ellipsizeToWidth(this.hint))
                    .append("\n")
                    .append(ellipsizeToWidth(this.subHint)));
        } else {
            super.setHint(ellipsizeToWidth(this.hint));
        }
    }

    public void setCursorPositionChangedListener(@Nullable CursorPositionChangedListener listener) {
        this.cursorPositionChangedListener = listener;
    }

    public void setTransport() {
        final boolean useSystemEmoji = TextSecurePreferences.isSystemEmojiPreferred(getContext());
        final boolean isIncognito    = TextSecurePreferences.isIncognitoKeyboardEnabled(getContext());

        int imeOptions = (getImeOptions() & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_SEND;
        int inputType  = getInputType();

        setImeActionLabel(null, 0);

        if (useSystemEmoji) {
            inputType = (inputType & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
        }

        setInputType(inputType);
        if (isIncognito) {
            setImeOptions(imeOptions | 16777216);
        } else {
            setImeOptions(imeOptions);
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        InputConnection inputConnection = super.onCreateInputConnection(editorInfo);

        if(TextSecurePreferences.isEnterSendsEnabled(getContext())) {
            editorInfo.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }

        if (Build.VERSION.SDK_INT < 21) return inputConnection;
        if (mediaListener == null)      return inputConnection;
        if (inputConnection == null)    return null;

        EditorInfoCompat.setContentMimeTypes(editorInfo, new String[] {"image/jpeg", "image/png", "image/gif"});
        return InputConnectionCompat.createWrapper(inputConnection, editorInfo, new CommitContentListener(mediaListener));
    }

    public void setMediaListener(@Nullable InputPanel.MediaListener mediaListener) {
        this.mediaListener = mediaListener;
    }

    private void initialize() {
        if (TextSecurePreferences.isIncognitoKeyboardEnabled(getContext())) {
            setImeOptions(getImeOptions() | 16777216);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR2)
    private static class CommitContentListener implements InputConnectionCompat.OnCommitContentListener {

        private static final String TAG = CommitContentListener.class.getSimpleName();

        private final InputPanel.MediaListener mediaListener;

        private CommitContentListener(@NonNull InputPanel.MediaListener mediaListener) {
            this.mediaListener = mediaListener;
        }

        @Override
        public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts) {
            if (BuildCompat.isAtLeastNMR1() && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    inputContentInfo.requestPermission();
                } catch (Exception e) {
                    Log.w(TAG, e);
                    return false;
                }
            }

            if (inputContentInfo.getDescription().getMimeTypeCount() > 0) {
                mediaListener.onMediaSelected(inputContentInfo.getContentUri(),
                        inputContentInfo.getDescription().getMimeType(0));

                return true;
            }

            return false;
        }
    }

    public interface CursorPositionChangedListener {
        void onCursorPositionChanged(int start, int end);
    }
}
