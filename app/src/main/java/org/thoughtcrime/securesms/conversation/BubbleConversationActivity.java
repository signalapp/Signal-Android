package org.thoughtcrime.securesms.conversation;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

/**
 * Activity which encapsulates a conversation for a Bubble window.
 *
 * This activity exists so that we can override some of its manifest parameters
 * without clashing with {@link ConversationActivity} and provide an API-level
 * independent "is in bubble?" check.
 */
public class BubbleConversationActivity extends ConversationActivity {
  @Override
  public boolean isInBubble() {
    return true;
  }

  @Override
  public void onInitializeToolbar(@NonNull Toolbar toolbar) {
  }
}
