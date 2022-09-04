package org.thoughtcrime.securesms.conversation.v2.messages;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;

import org.session.libsession.utilities.TextSecurePreferences;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.conversation.v2.ViewUtil;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.util.NumberUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import network.loki.messenger.R;

public class EmojiReactionsView extends LinearLayout implements View.OnTouchListener {

  // Normally 6dp, but we have 1dp left+right margin on the pills themselves
  private final int OUTER_MARGIN = ViewUtil.dpToPx(2);
  private static final int DEFAULT_THRESHOLD = 5;

  private List<ReactionRecord> records;
  private long                 messageId;
  private ViewGroup            container;
  private Group                showLess;
  private VisibleMessageViewDelegate delegate;
  private Handler gestureHandler = new Handler(Looper.getMainLooper());
  private Runnable pressCallback;
  private Runnable longPressCallback;
  private long onDownTimestamp = 0;
  private static long longPressDurationThreshold = 250;
  private static long maxDoubleTapInterval = 200;
  private boolean extended = false;

  public EmojiReactionsView(Context context) {
    super(context);
    init(null);
  }

  public EmojiReactionsView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.view_emoji_reactions, this);

    this.container = findViewById(R.id.layout_emoji_container);
    this.showLess = findViewById(R.id.group_show_less);

    records = new ArrayList<>();

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiReactionsView, 0, 0);
      typedArray.recycle();
    }
  }

  public void clear() {
    this.records.clear();
    container.removeAllViews();
  }

  public void setReactions(long messageId, @NonNull List<ReactionRecord> records, boolean outgoing, VisibleMessageViewDelegate delegate) {
    this.delegate = delegate;
    if (records.equals(this.records)) {
      return;
    }

    FlexboxLayout containerLayout = (FlexboxLayout) this.container;
    containerLayout.setJustifyContent(outgoing ? JustifyContent.FLEX_END : JustifyContent.FLEX_START);
    this.records.clear();
    this.records.addAll(records);
    if (this.messageId != messageId) {
      extended = false;
    }
    this.messageId = messageId;

    displayReactions(extended ? Integer.MAX_VALUE : DEFAULT_THRESHOLD);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    if (v.getTag() == null) return false;

    Reaction reaction = (Reaction) v.getTag();
    int action = event.getAction();
    if (action == MotionEvent.ACTION_DOWN) onDown(new MessageId(reaction.messageId, reaction.isMms));
    else if (action == MotionEvent.ACTION_CANCEL) removeLongPressCallback();
    else if (action == MotionEvent.ACTION_UP) onUp(reaction);
    return true;
  }

  private void displayReactions(int threshold) {
    String userPublicKey     = TextSecurePreferences.getLocalNumber(getContext());
    List<Reaction> reactions = buildSortedReactionsList(records, userPublicKey, threshold);

    container.removeAllViews();
    LinearLayout overflowContainer = new LinearLayout(getContext());
    overflowContainer.setOrientation(LinearLayout.HORIZONTAL);
    int innerPadding = ViewUtil.dpToPx(4);
    overflowContainer.setPaddingRelative(innerPadding,innerPadding,innerPadding,innerPadding);

    for (Reaction reaction : reactions) {
      if (container.getChildCount() + 1 >= DEFAULT_THRESHOLD && threshold != Integer.MAX_VALUE && reactions.size() > threshold) {
        if (overflowContainer.getParent() == null) {
          container.addView(overflowContainer);
          ViewGroup.LayoutParams overflowParams = overflowContainer.getLayoutParams();
          overflowParams.height = ViewUtil.dpToPx(26);
          overflowContainer.setLayoutParams(overflowParams);
          overflowContainer.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.reaction_pill_dialog_background));
        }
        View pill = buildPill(getContext(), this, reaction, true);
        pill.setOnClickListener(v -> {
          extended = true;
          displayReactions(Integer.MAX_VALUE);
        });
        pill.findViewById(R.id.reactions_pill_count).setVisibility(View.GONE);
        pill.findViewById(R.id.reactions_pill_spacer).setVisibility(View.GONE);
        overflowContainer.addView(pill);
      } else {
        View pill = buildPill(getContext(), this, reaction, false);
        pill.setTag(reaction);
        pill.setOnTouchListener(this);
        container.addView(pill);
        int pixelSize = ViewUtil.dpToPx(1);
        MarginLayoutParams params = (MarginLayoutParams) pill.getLayoutParams();
        params.setMargins(pixelSize, 0, pixelSize, 0);
        pill.setLayoutParams(params);
      }
    }

    int overflowChildren = overflowContainer.getChildCount();
    int negativeMargin = ViewUtil.dpToPx(-8);
    for (int i = 0; i < overflowChildren; i++) {
      View child = overflowContainer.getChildAt(i);
      MarginLayoutParams childParams = (MarginLayoutParams) child.getLayoutParams();
      if ((i == 0 && overflowChildren > 1) || i + 1 < overflowChildren) {
        // if first and there is more than one child, or we are not the last child then set negative right margin
        childParams.setMargins(0,0, negativeMargin, 0);
        child.setLayoutParams(childParams);
      }
    }

    if (threshold == Integer.MAX_VALUE) {
      showLess.setVisibility(VISIBLE);
      for (int id : showLess.getReferencedIds()) {
        findViewById(id).setOnClickListener(view -> {
          extended = false;
          displayReactions(DEFAULT_THRESHOLD);
        });
      }
    } else {
      showLess.setVisibility(GONE);
    }
  }

  private void onReactionClicked(Reaction reaction) {
    if (reaction.messageId != 0) {
      MessageId messageId = new MessageId(reaction.messageId, reaction.isMms);
      delegate.onReactionClicked(reaction.emoji, messageId, reaction.userWasSender);
    }
  }

  private static @NonNull List<Reaction> buildSortedReactionsList(@NonNull List<ReactionRecord> records, String userPublicKey, int threshold) {
    Map<String, Reaction> counters = new LinkedHashMap<>();

    for (ReactionRecord record : records) {
      String   baseEmoji = EmojiUtil.getCanonicalRepresentation(record.getEmoji());
      Reaction info      = counters.get(baseEmoji);

      if (info == null) {
        info = new Reaction(record.getMessageId(), record.isMms(), record.getEmoji(), record.getCount(), record.getSortId(), record.getDateReceived(), userPublicKey.equals(record.getAuthor()));
      } else {
        info.update(record.getEmoji(), record.getCount(), record.getDateReceived(), userPublicKey.equals(record.getAuthor()));
      }

      counters.put(baseEmoji, info);
    }

    List<Reaction> reactions = new ArrayList<>(counters.values());

    Collections.sort(reactions, Collections.reverseOrder());

    if (reactions.size() >= threshold + 2 && threshold != Integer.MAX_VALUE) {
      List<Reaction> shortened = new ArrayList<>(threshold + 2);
      shortened.addAll(reactions.subList(0, threshold + 2));
      return shortened;
    } else {
      return reactions;
    }
  }

  private static View buildPill(@NonNull Context context, @NonNull ViewGroup parent, @NonNull Reaction reaction, boolean isCompact) {
    View           root      = LayoutInflater.from(context).inflate(R.layout.reactions_pill, parent, false);
    EmojiImageView emojiView = root.findViewById(R.id.reactions_pill_emoji);
    TextView       countView = root.findViewById(R.id.reactions_pill_count);
    View           spacer    = root.findViewById(R.id.reactions_pill_spacer);

    if (isCompact) {
      root.setPaddingRelative(1,1,1,1);
      ViewGroup.LayoutParams layoutParams = root.getLayoutParams();
      layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
      root.setLayoutParams(layoutParams);
    }

    if (reaction.emoji != null) {
      emojiView.setImageEmoji(reaction.emoji);

      if (reaction.count >= 1) {
        countView.setText(NumberUtil.getFormattedNumber(reaction.count));
      } else {
        countView.setVisibility(GONE);
        spacer.setVisibility(GONE);
      }
    } else {
      emojiView.setVisibility(GONE);
      spacer.setVisibility(GONE);
      countView.setText(context.getString(R.string.ReactionsConversationView_plus, reaction.count));
    }

    if (reaction.userWasSender && !isCompact) {
      root.setBackground(ContextCompat.getDrawable(context, R.drawable.reaction_pill_background_selected));
      countView.setTextColor(ContextCompat.getColor(context, R.color.reactions_pill_selected_text_color));
    } else {
      if (!isCompact) {
        root.setBackground(ContextCompat.getDrawable(context, R.drawable.reaction_pill_background));
      }
    }

    return root;
  }

  private void onDown(MessageId messageId) {
    removeLongPressCallback();
    Runnable newLongPressCallback = () -> {
      performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
      if (delegate != null) {
        delegate.onReactionLongClicked(messageId);
      }
    };
    this.longPressCallback = newLongPressCallback;
    gestureHandler.postDelayed(newLongPressCallback, longPressDurationThreshold);
    onDownTimestamp = new Date().getTime();
  }

  private void removeLongPressCallback() {
    if (longPressCallback != null) {
      gestureHandler.removeCallbacks(longPressCallback);
    }
  }

  private void onUp(Reaction reaction) {
    if ((new Date().getTime() - onDownTimestamp) < longPressDurationThreshold) {
      removeLongPressCallback();
      if (pressCallback != null) {
        gestureHandler.removeCallbacks(pressCallback);
        this.pressCallback = null;
      } else {
        Runnable newPressCallback = () -> {
          onReactionClicked(reaction);
          pressCallback = null;
        };
        this.pressCallback = newPressCallback;
        gestureHandler.postDelayed(newPressCallback, maxDoubleTapInterval);
      }
    }
  }

  private static class Reaction implements Comparable<Reaction> {
    private final long messageId;
    private final boolean isMms;
    private String  emoji;
    private long    count;
    private long    sortIndex;
    private long    lastSeen;
    private boolean userWasSender;

    Reaction(long messageId, boolean isMms, @Nullable String emoji, long count, long sortIndex, long lastSeen, boolean userWasSender) {
      this.messageId     = messageId;
      this.isMms         = isMms;
      this.emoji         = emoji;
      this.count         = count;
      this.sortIndex     = sortIndex;
      this.lastSeen      = lastSeen;
      this.userWasSender = userWasSender;
    }

    void update(@NonNull String emoji, long count, long lastSeen, boolean userWasSender) {
      if (!this.userWasSender) {
        if (userWasSender || lastSeen > this.lastSeen) {
          this.emoji = emoji;
        }
      }

      this.count         = this.count + count;
      this.lastSeen      = Math.max(this.lastSeen, lastSeen);
      this.userWasSender = this.userWasSender || userWasSender;
    }

    @NonNull Reaction merge(@NonNull Reaction other) {
      this.count         = this.count + other.count;
      this.lastSeen      = Math.max(this.lastSeen, other.lastSeen);
      this.userWasSender = this.userWasSender || other.userWasSender;
      return this;
    }

    @Override
    public int compareTo(Reaction rhs) {
      Reaction lhs = this;
      if (lhs.count == rhs.count ) {
        return Long.compare(lhs.sortIndex, rhs.sortIndex);
      } else {
        return Long.compare(lhs.count, rhs.count);
      }
    }
  }
}
