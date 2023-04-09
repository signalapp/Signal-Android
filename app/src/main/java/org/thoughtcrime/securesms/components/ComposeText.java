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
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import org.signal.core.util.StringUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.components.mention.MentionDeleter;
import org.thoughtcrime.securesms.components.mention.MentionRendererDelegate;
import org.thoughtcrime.securesms.components.mention.MentionValidatorWatcher;
import org.thoughtcrime.securesms.components.spoiler.SpoilerRendererDelegate;
import org.thoughtcrime.securesms.conversation.MessageSendType;
import org.thoughtcrime.securesms.conversation.MessageStyler;
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery;
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryChangedListener;
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryReplacement;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.thoughtcrime.securesms.database.MentionUtil.MENTION_STARTER;

public class ComposeText extends EmojiEditText {

  private static final char EMOJI_STARTER       = ':';

  private static final Pattern TIME_PATTERN = Pattern.compile("^[0-9]{1,2}:[0-9]{1,2}$");

  private CharSequence            hint;
  private SpannableString         subHint;
  private MentionRendererDelegate mentionRendererDelegate;
  private SpoilerRendererDelegate spoilerRendererDelegate;
  private MentionValidatorWatcher mentionValidatorWatcher;

  @Nullable private InputPanel.MediaListener      mediaListener;
  @Nullable private CursorPositionChangedListener cursorPositionChangedListener;
  @Nullable private InlineQueryChangedListener    inlineQueryChangedListener;

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

    if (getLayout() != null && !TextUtils.isEmpty(hint)) {
      if (!TextUtils.isEmpty(subHint)) {
        setHintWithChecks(new SpannableStringBuilder().append(ellipsizeToWidth(hint))
                                                      .append("\n")
                                                      .append(ellipsizeToWidth(subHint)));
      } else {
        setHintWithChecks(ellipsizeToWidth(hint));
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
        clearInlineQuery();
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
        if (spoilerRendererDelegate != null) {
          spoilerRendererDelegate.draw(canvas, getText(), getLayout());
        }
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
      setHintWithChecks(new SpannableStringBuilder().append(ellipsizeToWidth(this.hint))
                                                    .append("\n")
                                                    .append(ellipsizeToWidth(this.subHint)));
    } else {
      setHintWithChecks(ellipsizeToWidth(this.hint));
    }

    setHintWithChecks(hint);
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

  public void setInlineQueryChangedListener(@Nullable InlineQueryChangedListener listener) {
    this.inlineQueryChangedListener = listener;
  }

  public void setMentionValidator(@Nullable MentionValidatorWatcher.MentionValidator mentionValidator) {
    mentionValidatorWatcher.setMentionValidator(mentionValidator);
  }

  private boolean isLandscape() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public void setMessageSendType(MessageSendType messageSendType) {
    final boolean useSystemEmoji = SignalStore.settings().isPreferSystemEmoji();

    int imeOptions = (getImeOptions() & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_SEND;
    int inputType  = getInputType();

    if (isLandscape()) setImeActionLabel(getContext().getString(messageSendType.getComposeHintRes()), EditorInfo.IME_ACTION_SEND);
    else               setImeActionLabel(null, 0);

    if (useSystemEmoji) {
      inputType = (inputType & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
    }

    setImeOptions(imeOptions);
    setHint(getContext().getString(messageSendType.getComposeHintRes()),
            messageSendType.getSimName() != null
                ? getContext().getString(R.string.conversation_activity__from_sim_name, messageSendType.getSimName())
                : null);
    setInputType(inputType);
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
    InputConnection inputConnection = super.onCreateInputConnection(editorInfo);

    if (SignalStore.settings().isEnterKeySends()) {
      editorInfo.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
    }

    if (mediaListener == null) {
      return inputConnection;
    }

    if (inputConnection == null) {
      return null;
    }

    EditorInfoCompat.setContentMimeTypes(editorInfo, new String[] { "image/jpeg", "image/png", "image/gif" });
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

  public boolean hasStyling() {
    CharSequence trimmed = getTextTrimmed();
    return FeatureFlags.textFormatting() && (trimmed instanceof Spanned) && MessageStyler.hasStyling((Spanned) trimmed);
  }

  public @Nullable BodyRangeList getStyling() {
    if (FeatureFlags.textFormatting()) {
      return MessageStyler.getStyling(getTextTrimmed());
    } else {
      return null;
    }
  }

  private void initialize() {
    if (TextSecurePreferences.isIncognitoKeyboardEnabled(getContext())) {
      setImeOptions(getImeOptions() | 16777216);
    }

    mentionRendererDelegate = new MentionRendererDelegate(getContext(), ContextCompat.getColor(getContext(), R.color.conversation_mention_background_color));

    addTextChangedListener(new MentionDeleter());
    mentionValidatorWatcher = new MentionValidatorWatcher();
    addTextChangedListener(mentionValidatorWatcher);

    if (FeatureFlags.textFormatting()) {
      if (FeatureFlags.textFormattingSpoilerSend()) {
        spoilerRendererDelegate = new SpoilerRendererDelegate(this, true);
      }

      addTextChangedListener(new ComposeTextStyleWatcher());

      setCustomSelectionActionModeCallback(new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
          MenuItem copy         = menu.findItem(android.R.id.copy);
          MenuItem cut          = menu.findItem(android.R.id.cut);
          MenuItem paste        = menu.findItem(android.R.id.paste);
          int      copyOrder    = copy != null ? copy.getOrder() : 0;
          int      cutOrder     = cut != null ? cut.getOrder() : 0;
          int      pasteOrder   = paste != null ? paste.getOrder() : 0;
          int      largestOrder = Math.max(copyOrder, Math.max(cutOrder, pasteOrder));

          menu.add(0, R.id.edittext_bold, largestOrder, getContext().getString(R.string.TextFormatting_bold));
          menu.add(0, R.id.edittext_italic, largestOrder, getContext().getString(R.string.TextFormatting_italic));
          menu.add(0, R.id.edittext_strikethrough, largestOrder, getContext().getString(R.string.TextFormatting_strikethrough));
          menu.add(0, R.id.edittext_monospace, largestOrder, getContext().getString(R.string.TextFormatting_monospace));

          if (FeatureFlags.textFormattingSpoilerSend()) {
            menu.add(0, R.id.edittext_spoiler, largestOrder, getContext().getString(R.string.TextFormatting_spoiler));
          }

          return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
          Editable text = getText();

          if (text == null) {
            return false;
          }

          if (item.getItemId() != R.id.edittext_bold &&
              item.getItemId() != R.id.edittext_italic &&
              item.getItemId() != R.id.edittext_strikethrough &&
              item.getItemId() != R.id.edittext_monospace &&
              item.getItemId() != R.id.edittext_spoiler) {
            return false;
          }

          int start = getSelectionStart();
          int end   = getSelectionEnd();

          CharSequence    charSequence = text.subSequence(start, end);
          SpannableString replacement  = new SpannableString(charSequence);
          Object          style        = null;

          if (item.getItemId() == R.id.edittext_bold) {
            style = MessageStyler.boldStyle();
          } else if (item.getItemId() == R.id.edittext_italic) {
            style = MessageStyler.italicStyle();
          } else if (item.getItemId() == R.id.edittext_strikethrough) {
            style = MessageStyler.strikethroughStyle();
          } else if (item.getItemId() == R.id.edittext_monospace) {
            style = MessageStyler.monoStyle();
          } else if (item.getItemId() == R.id.edittext_spoiler) {
            style = MessageStyler.spoilerStyle(MessageStyler.COMPOSE_ID, start, charSequence.length());
          }

          if (style != null) {
            replacement.setSpan(style, 0, charSequence.length(), MessageStyler.SPAN_FLAGS);
          }

          clearComposingText();

          text.replace(start, end, replacement);

          mode.finish();
          return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
          return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {}
      });
    }
  }

  private void setHintWithChecks(@Nullable CharSequence newHint) {
    if (getLayout() == null || Objects.equals(getHint(), newHint)) {
      return;
    }

    setHint(newHint);
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
    if (enoughToFilter(text, false)) {
      performFiltering(text, false);
    } else {
      clearInlineQuery();
    }
  }

  private void performFiltering(@NonNull Editable text, boolean keywordEmojiSearch) {
    int        end        = getSelectionEnd();
    QueryStart queryStart = findQueryStart(text, end, keywordEmojiSearch);
    int        start      = queryStart.index;
    String     query      = text.subSequence(start, end).toString();

    if (inlineQueryChangedListener != null) {
      if (queryStart.isMentionQuery) {
        inlineQueryChangedListener.onQueryChanged(new InlineQuery.Mention(query));
      } else {
        inlineQueryChangedListener.onQueryChanged(new InlineQuery.Emoji(query, keywordEmojiSearch));
      }
    }
  }

  private void clearInlineQuery() {
    if (inlineQueryChangedListener != null) {
      inlineQueryChangedListener.clearQuery();
    }
  }

  private boolean enoughToFilter(@NonNull Editable text, boolean keywordEmojiSearch) {
    int end = getSelectionEnd();
    if (end < 0) {
      return false;
    }
    return findQueryStart(text, end, keywordEmojiSearch).index != -1;
  }

  public void replaceTextWithMention(@NonNull String displayName, @NonNull RecipientId recipientId) {
    replaceText(createReplacementToken(displayName, recipientId), false);
  }

  public void replaceText(@NonNull InlineQueryReplacement replacement) {
    replaceText(replacement.toCharSequence(getContext()), replacement.isKeywordSearch());
  }

  private void replaceText(@NonNull CharSequence replacement, boolean keywordReplacement) {
    Editable text = getText();
    if (text == null) {
      return;
    }

    clearComposingText();

    int end   = getSelectionEnd();
    int start = findQueryStart(text, end, keywordReplacement).index - (keywordReplacement ? 0 : 1);

    text.replace(start, end, "");
    text.insert(start, replacement);
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

  private QueryStart findQueryStart(@NonNull CharSequence text, int inputCursorPosition, boolean keywordEmojiSearch) {
    if (keywordEmojiSearch) {
      int start = findQueryStart(text, inputCursorPosition, ' ');
      if (start == -1 && inputCursorPosition != 0) {
        start = 0;
      } else if (start == inputCursorPosition) {
        start = -1;
      }
      return new QueryStart(start, false);
    }

    QueryStart queryStart = new QueryStart(findQueryStart(text, inputCursorPosition, MENTION_STARTER), true);

    if (queryStart.index < 0) {
      queryStart = new QueryStart(findQueryStart(text, inputCursorPosition, EMOJI_STARTER), false);
    }

    return queryStart;
  }

  private int findQueryStart(@NonNull CharSequence text, int inputCursorPosition, char starter) {
    if (inputCursorPosition == 0) {
      return -1;
    }

    int delimiterSearchIndex = inputCursorPosition - 1;
    while (delimiterSearchIndex >= 0 && (text.charAt(delimiterSearchIndex) != starter && !Character.isWhitespace(text.charAt(delimiterSearchIndex)))) {
      delimiterSearchIndex--;
    }

    if (delimiterSearchIndex >= 0 && text.charAt(delimiterSearchIndex) == starter) {
      if (couldBeTimeEntry(text, delimiterSearchIndex)) {
        return -1;
      } else {
        return delimiterSearchIndex + 1;
      }
    }
    return -1;
  }

  @Override
  protected boolean shouldPersistSignalStylingWhenPasting() {
    return true;
  }

  /**
   * Return true if we think the user may be inputting a time.
   */
  private static boolean couldBeTimeEntry(@NonNull CharSequence text, int startIndex) {
    if (startIndex <= 0 || startIndex + 1 >= text.length()) {
      return false;
    }

    int startOfToken = startIndex;
    while (startOfToken > 0 && !Character.isWhitespace(text.charAt(startOfToken))) {
      startOfToken--;
    }
    startOfToken++;

    int endOfToken = startIndex;
    while (endOfToken < text.length() && !Character.isWhitespace(text.charAt(endOfToken))) {
      endOfToken++;
    }

    return TIME_PATTERN.matcher(text.subSequence(startOfToken, endOfToken)).find();
  }

  private static class CommitContentListener implements InputConnectionCompat.OnCommitContentListener {

    private static final String TAG = Log.tag(CommitContentListener.class);

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

  private static class QueryStart {
    public int     index;
    public boolean isMentionQuery;

    public QueryStart(int index, boolean isMentionQuery) {
      this.index          = index;
      this.isMentionQuery = isMentionQuery;
    }
  }

  public interface CursorPositionChangedListener {
    void onCursorPositionChanged(int start, int end);
  }

}
