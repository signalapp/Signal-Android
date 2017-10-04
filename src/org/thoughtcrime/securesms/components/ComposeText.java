package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.os.BuildCompat;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class ComposeText extends EmojiEditText {

  private CharSequence    hint;
  private SpannableString subHint;

  @Nullable private InputPanel.MediaListener mediaListener;

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

  public void appendInvite(String invite) {
    if (!TextUtils.isEmpty(getText()) && !getText().toString().equals(" ")) {
      append(" ");
    }

    append(invite);
    setSelection(getText().length());
  }

  private boolean isLandscape() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public void setTransport(TransportOption transport) {
    final boolean useSystemEmoji = TextSecurePreferences.isSystemEmojiPreferred(getContext());
    final boolean isIncognito    = TextSecurePreferences.isIncognitoKeyboardEnabled(getContext());

    int imeOptions = (getImeOptions() & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_SEND;
    int inputType  = getInputType();

    if (isLandscape()) setImeActionLabel(transport.getComposeHint(), EditorInfo.IME_ACTION_SEND);
    else               setImeActionLabel(null, 0);

    if (useSystemEmoji) {
      inputType = (inputType & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
    }

    setInputType(inputType);
    setImeOptions(imeOptions);
    setHint(transport.getComposeHint(),
            transport.getSimName().isPresent()
                ? getContext().getString(R.string.conversation_activity__from_sim_name, transport.getSimName().get())
                : null);
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

    private static final String TAG = CommitContentListener.class.getName();

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

}
