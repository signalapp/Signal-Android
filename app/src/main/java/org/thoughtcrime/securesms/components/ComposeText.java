package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.text.Annotation;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.components.mention.MentionDeleter;
import org.thoughtcrime.securesms.components.mention.MentionRendererDelegate;
import org.thoughtcrime.securesms.components.mention.MentionValidatorWatcher;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.StringUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.List;

import static org.thoughtcrime.securesms.database.MentionUtil.MENTION_STARTER;

public class ComposeText extends EmojiEditText {

  private CharSequence            hint;
  private SpannableString         subHint;
  private MentionRendererDelegate mentionRendererDelegate;
  private MentionValidatorWatcher mentionValidatorWatcher;

  @Nullable private InputPanel.MediaListener      mediaListener;
  @Nullable private CursorPositionChangedListener cursorPositionChangedListener;
  @Nullable private MentionQueryChangedListener   mentionQueryChangedListener;

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

  /**
   * Trims and returns text while preserving potential spans like {@link MentionAnnotation}.
   */
  public @NonNull CharSequence getTextTrimmed() {
    Editable text = getText();
    if (text == null) {
      return "";
    }
    return StringUtil.trimSequence(text);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (!TextUtils.isEmpty(hint)) {
      if (!TextUtils.isEmpty(subHint)) {
        setHint(new SpannableStringBuilder().append(ellipsizeToWidth(hint))
                                            .append("\n")
                                            .append(ellipsizeToWidth(subHint)));
      } else {
        setHint(ellipsizeToWidth(hint));
      }
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override
  protected void onSelectionChanged(int selectionStart, int selectionEnd) {
    super.onSelectionChanged(selectionStart, selectionEnd);

    if (getText() != null) {
      boolean selectionChanged = changeSelectionForPartialMentions(getText(), selectionStart, selectionEnd);
      if (selectionChanged) {
        return;
      }

      if (selectionStart == selectionEnd) {
        doAfterCursorChange(getText());
      } else {
        updateQuery(null);
      }
    }

    if (cursorPositionChangedListener != null) {
      cursorPositionChangedListener.onCursorPositionChanged(selectionStart, selectionEnd);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (getText() != null && getLayout() != null) {
      int checkpoint = canvas.save();

      // Clip using same logic as TextView drawing
      int   maxScrollY = getLayout().getHeight() - getBottom() - getTop() - getCompoundPaddingBottom() - getCompoundPaddingTop();
      float clipLeft   = getCompoundPaddingLeft() + getScrollX();
      float clipTop    = (getScrollY() == 0) ? 0 : getExtendedPaddingTop() + getScrollY();
      float clipRight  = getRight() - getLeft() - getCompoundPaddingRight() + getScrollX();
      float clipBottom = getBottom() - getTop() + getScrollY() - ((getScrollY() == maxScrollY) ? 0 : getExtendedPaddingBottom());

      canvas.clipRect(clipLeft - 10, clipTop, clipRight + 10, clipBottom);
      canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());

      try {
        mentionRendererDelegate.draw(canvas, getText(), getLayout());
      } finally {
        canvas.restoreToCount(checkpoint);
      }
    }
    super.onDraw(canvas);
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

    super.setHint(hint);
  }

  public void appendInvite(String invite) {
    if (getText() == null) {
      return;
    }

    if (!TextUtils.isEmpty(getText()) && !getText().toString().equals(" ")) {
      append(" ");
    }

    append(invite);
    setSelection(getText().length());
  }

  public void setCursorPositionChangedListener(@Nullable CursorPositionChangedListener listener) {
    this.cursorPositionChangedListener = listener;
  }

  public void setMentionQueryChangedListener(@Nullable MentionQueryChangedListener listener) {
    this.mentionQueryChangedListener = listener;
  }

  public void setMentionValidator(@Nullable MentionValidatorWatcher.MentionValidator mentionValidator) {
    mentionValidatorWatcher.setMentionValidator(mentionValidator);
  }

  private boolean isLandscape() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public void setTransport(TransportOption transport) {
    final boolean useSystemEmoji = TextSecurePreferences.isSystemEmojiPreferred(getContext());

    int imeOptions = (getImeOptions() & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_SEND;
    int inputType  = getInputType();

    if (isLandscape()) setImeActionLabel(transport.getComposeHint(), EditorInfo.IME_ACTION_SEND);
    else               setImeActionLabel(null, 0);

    if (useSystemEmoji) {
      inputType = (inputType & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
    }

    setImeOptions(imeOptions);
    setHint(transport.getComposeHint(),
            transport.getSimName().isPresent()
                ? getContext().getString(R.string.conversation_activity__from_sim_name, transport.getSimName().get())
                : null);
    setInputType(inputType);
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

  public boolean hasMentions() {
    Editable text = getText();
    if (text != null) {
      return !MentionAnnotation.getMentionAnnotations(text).isEmpty();
    }
    return false;
  }

  public @NonNull List<Mention> getMentions() {
    return MentionAnnotation.getMentionsFromAnnotations(getText());
  }

  private void initialize() {
    if (TextSecurePreferences.isIncognitoKeyboardEnabled(getContext())) {
      setImeOptions(getImeOptions() | 16777216);
    }

    mentionRendererDelegate = new MentionRendererDelegate(getContext(), ContextCompat.getColor(getContext(), R.color.conversation_mention_background_color));

    addTextChangedListener(new MentionDeleter());
    mentionValidatorWatcher = new MentionValidatorWatcher();
    addTextChangedListener(mentionValidatorWatcher);
  }

  private boolean changeSelectionForPartialMentions(@NonNull Spanned spanned, int selectionStart, int selectionEnd) {
    Annotation[] annotations = spanned.getSpans(0, spanned.length(), Annotation.class);
    for (Annotation annotation : annotations) {
      if (MentionAnnotation.isMentionAnnotation(annotation)) {
        int spanStart = spanned.getSpanStart(annotation);
        int spanEnd   = spanned.getSpanEnd(annotation);

        boolean startInMention = selectionStart > spanStart && selectionStart < spanEnd;
        boolean endInMention   = selectionEnd > spanStart && selectionEnd < spanEnd;

        if (startInMention || endInMention) {
          if (selectionStart == selectionEnd) {
            setSelection(spanEnd, spanEnd);
          } else {
            int newStart = startInMention ? spanStart : selectionStart;
            int newEnd   = endInMention ? spanEnd : selectionEnd;
            setSelection(newStart, newEnd);
          }
          return true;
        }
      }
    }
    return false;
  }

  private void doAfterCursorChange(@NonNull Editable text) {
    if (enoughToFilter(text)) {
      performFiltering(text);
    } else {
      updateQuery(null);
    }
  }

  private void performFiltering(@NonNull Editable text) {
    int          end   = getSelectionEnd();
    int          start = findQueryStart(text, end);
    CharSequence query = text.subSequence(start, end);
    updateQuery(query.toString());
  }

  private void updateQuery(@Nullable String query) {
    if (mentionQueryChangedListener != null) {
      mentionQueryChangedListener.onQueryChanged(query);
    }
  }

  private boolean enoughToFilter(@NonNull Editable text) {
    int end = getSelectionEnd();
    if (end < 0) {
      return false;
    }
    return findQueryStart(text, end) != -1;
  }

  public void replaceTextWithMention(@NonNull String displayName, @NonNull RecipientId recipientId) {
    Editable text = getText();
    if (text == null) {
      return;
    }

    clearComposingText();

    int    end      = getSelectionEnd();
    int    start    = findQueryStart(text, end) - 1;

    text.replace(start, end, createReplacementToken(displayName, recipientId));
  }

  private @NonNull CharSequence createReplacementToken(@NonNull CharSequence text, @NonNull RecipientId recipientId) {
    SpannableStringBuilder builder = new SpannableStringBuilder().append(MENTION_STARTER);
    if (text instanceof Spanned) {
      SpannableString spannableString = new SpannableString(text + " ");
      TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, spannableString, 0);
      builder.append(spannableString);
    } else {
      builder.append(text).append(" ");
    }

    builder.setSpan(MentionAnnotation.mentionAnnotationForRecipientId(recipientId), 0, builder.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    return builder;
  }

  private int findQueryStart(@NonNull CharSequence text, int inputCursorPosition) {
    if (inputCursorPosition == 0) {
      return -1;
    }

    int delimiterSearchIndex = inputCursorPosition - 1;
    while (delimiterSearchIndex >= 0 && (text.charAt(delimiterSearchIndex) != MENTION_STARTER && text.charAt(delimiterSearchIndex) != ' ')) {
      delimiterSearchIndex--;
    }

    if (delimiterSearchIndex >= 0 && text.charAt(delimiterSearchIndex) == MENTION_STARTER) {
      return delimiterSearchIndex + 1;
    }
    return -1;
  }

  private static class CommitContentListener implements InputConnectionCompat.OnCommitContentListener {

    private static final String TAG = CommitContentListener.class.getSimpleName();

    private final InputPanel.MediaListener mediaListener;

    private CommitContentListener(@NonNull InputPanel.MediaListener mediaListener) {
      this.mediaListener = mediaListener;
    }

    @Override
    public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts) {
      if (Build.VERSION.SDK_INT >= 25 && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
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

  public interface MentionQueryChangedListener {
    void onQueryChanged(@Nullable String query);
  }
}
