package org.thoughtcrime.securesms.reactions;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReactionsConversationView extends LinearLayout {

  // Normally 6dp, but we have 1dp left+right margin on the pills themselves
  private static final int OUTER_MARGIN = ViewUtil.dpToPx(5);

  private boolean              outgoing;
  private List<ReactionRecord> records;
  private int                  bubbleWidth;

  public ReactionsConversationView(Context context) {
    super(context);
    init(null);
  }

  public ReactionsConversationView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    records = new ArrayList<>();

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ReactionsConversationView, 0, 0);
      outgoing = typedArray.getBoolean(R.styleable.ReactionsConversationView_rcv_outgoing, false);
    }
  }

  public void clear() {
    this.records.clear();
    this.bubbleWidth = 0;
    removeAllViews();
  }

  public void setReactions(@NonNull List<ReactionRecord> records, int bubbleWidth) {
    if (records.equals(this.records) && this.bubbleWidth == bubbleWidth) {
      return;
    }

    this.records.clear();
    this.records.addAll(records);

    this.bubbleWidth = bubbleWidth;

    List<Reaction> reactions = buildSortedReactionsList(records);

    removeAllViews();

    for (Reaction reaction : reactions) {
      View pill = buildPill(getContext(), this, reaction);
      pill.setVisibility(bubbleWidth == 0 ? INVISIBLE : VISIBLE);
      addView(pill);
    }

    measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

    int railWidth = getMeasuredWidth();

    if (railWidth < (bubbleWidth - OUTER_MARGIN)) {
      int margin = (bubbleWidth - railWidth - OUTER_MARGIN);

      if (outgoing) {
        ViewUtil.setRightMargin(this, margin);
      } else {
        ViewUtil.setLeftMargin(this, margin);
      }
    } else {
      if (outgoing) {
        ViewUtil.setRightMargin(this, OUTER_MARGIN);
      } else {
        ViewUtil.setLeftMargin(this, OUTER_MARGIN);
      }
    }
  }

  private static @NonNull List<Reaction> buildSortedReactionsList(@NonNull List<ReactionRecord> records) {
    Map<String, Reaction> counters = new LinkedHashMap<>();
    RecipientId           selfId   = Recipient.self().getId();

    for (ReactionRecord record : records) {
      Reaction info = counters.get(record.getEmoji());

      if (info == null) {
        info = new Reaction(record.getEmoji(), 1, record.getDateReceived(), selfId.equals(record.getAuthor()));
      } else {
        info.update(record.getDateReceived(), selfId.equals(record.getAuthor()));
      }

      counters.put(record.getEmoji(), info);
    }

    List<Reaction> reactions = new ArrayList<>(counters.values());

    Collections.sort(reactions, Collections.reverseOrder());

    if (reactions.size() > 3) {
      List<Reaction> shortened = new ArrayList<>(3);
      shortened.add(reactions.get(0));
      shortened.add(reactions.get(1));
      shortened.add(Stream.of(reactions).skip(2).reduce(new Reaction(null, 0, 0, false), Reaction::merge));

      return shortened;
    } else {
      return reactions;
    }
  }

  private static View buildPill(@NonNull Context context, @NonNull ViewGroup parent, @NonNull Reaction reaction) {
    View     root      = LayoutInflater.from(context).inflate(R.layout.reactions_pill, parent, false);
    TextView emojiView = root.findViewById(R.id.reactions_pill_emoji);
    TextView countView = root.findViewById(R.id.reactions_pill_count);
    View     spacer    = root.findViewById(R.id.reactions_pill_spacer);

    if (reaction.emoji != null) {
      emojiView.setText(reaction.emoji);

      if (reaction.count > 1) {
        countView.setText(String.valueOf(reaction.count));
      } else {
        countView.setVisibility(GONE);
        spacer.setVisibility(GONE);
      }
    } else {
      emojiView.setVisibility(GONE);
      spacer.setVisibility(GONE);
      countView.setText(context.getString(R.string.ReactionsConversationView_plus, reaction.count));
    }

    if (reaction.userWasSender) {
      root.setBackground(ThemeUtil.getThemedDrawable(context, R.attr.reactions_pill_selected_background));
      countView.setTextColor(ThemeUtil.getThemedColor(context, R.attr.reactions_pill_selected_text_color));
    } else {
      root.setBackground(ThemeUtil.getThemedDrawable(context, R.attr.reactions_pill_background));
    }

    return root;
  }

  private static class Reaction implements Comparable<Reaction> {
    private String  emoji;
    private int     count;
    private long    lastSeen;
    private boolean userWasSender;

    Reaction(@Nullable String emoji, int count, long lastSeen, boolean userWasSender) {
      this.emoji         = emoji;
      this.count         = count;
      this.lastSeen      = lastSeen;
      this.userWasSender = userWasSender;
    }

    void update(long lastSeen, boolean userWasSender) {
      this.count         = this.count + 1;
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

      if (lhs.count != rhs.count) {
        return Integer.compare(lhs.count, rhs.count);
      }

      return Long.compare(lhs.lastSeen, rhs.lastSeen);
    }
  }
}
