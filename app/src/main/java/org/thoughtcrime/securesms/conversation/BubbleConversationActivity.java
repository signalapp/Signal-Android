package org.thoughtcrime.securesms.conversation;

/**
 * Activity which encapsulates a conversation for a Bubble window.
 *
 * This activity exists so that we can override some of its manifest parameters
 * without clashing with {@link ConversationActivity} and provide an API-level
 * independent "is in bubble?" check.
 */
public class BubbleConversationActivity extends ConversationActivity {
  @Override
  protected boolean isInBubble() {
    return true;
  }
}
