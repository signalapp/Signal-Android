package org.thoughtcrime.securesms.conversationlist;


import androidx.annotation.MainThread;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

public class ConversationListItemAnimator extends DefaultItemAnimator {

  public ConversationListItemAnimator() {
    setSupportsChangeAnimations(false);
  }

  @Override public boolean animateRemove(RecyclerView.ViewHolder holder) {
    return super.animateRemove(holder);
  }
  @MainThread
  public void disable() {
    setMoveDuration(0);
  }
}
