package org.thoughtcrime.securesms.conversationlist;


import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

public class ConversationListItemAnimator extends DefaultItemAnimator {

  public ConversationListItemAnimator() {
    setSupportsChangeAnimations(false);
  }

  @Override public boolean animateRemove(RecyclerView.ViewHolder holder) {
    return super.animateRemove(holder);
  }
}
