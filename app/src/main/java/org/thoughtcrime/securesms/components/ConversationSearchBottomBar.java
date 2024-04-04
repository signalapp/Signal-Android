package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.thoughtcrime.securesms.R;

/**
 * Bottom navigation bar shown in the {@link org.thoughtcrime.securesms.conversation.ConversationActivity}
 * when the user is searching within a conversation. Shows details about the results and allows the
 * user to move between them.
 */
public class ConversationSearchBottomBar extends ConstraintLayout {

  private View     searchDown;
  private View     searchUp;
  private TextView searchPositionText;
  private View     progressWheel;
  private View     jumpToDateButton;

  private EventListener eventListener;


  public ConversationSearchBottomBar(Context context) {
    super(context);
  }

  public ConversationSearchBottomBar(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    this.searchUp           = findViewById(R.id.conversation_search_up);
    this.searchDown         = findViewById(R.id.conversation_search_down);
    this.searchPositionText = findViewById(R.id.conversation_search_position);
    this.progressWheel      = findViewById(R.id.conversation_search_progress_wheel);
    this.jumpToDateButton   = findViewById(R.id.conversation_jump_to_date_button);
  }

  public void setData(int position, int count) {
    progressWheel.setVisibility(GONE);

    searchUp.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onSearchMoveUpPressed();
      }
    });

    searchDown.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onSearchMoveDownPressed();
      }
    });

    if (count > 0) {
      searchPositionText.setText(getResources().getString(R.string.ConversationActivity_search_position, position + 1, count));
    } else {
      searchPositionText.setText(R.string.ConversationActivity_no_results);
    }

    jumpToDateButton.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onDatePickerSelected();
      }
    });

    setViewEnabled(searchUp, position < (count - 1));
    setViewEnabled(searchDown, position > 0);
  }

  public void showLoading() {
    progressWheel.setVisibility(VISIBLE);
  }

  private void setViewEnabled(@NonNull View view, boolean enabled) {
    view.setEnabled(enabled);
    view.setAlpha(enabled ? 1f : 0.25f);
  }

  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener;
  }

  public interface EventListener {
    void onSearchMoveUpPressed();
    void onSearchMoveDownPressed();
    void onDatePickerSelected();
  }
}
