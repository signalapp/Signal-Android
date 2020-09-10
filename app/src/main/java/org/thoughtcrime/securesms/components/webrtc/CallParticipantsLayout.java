package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexboxLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.util.Util;

import java.util.Collections;
import java.util.List;

/**
 * Can dynamically render a collection of call participants, adjusting their
 * sizing and layout depending on the total number of participants.
 */
public class CallParticipantsLayout extends FlexboxLayout {

  private List<CallParticipant> callParticipants = Collections.emptyList();
  private boolean               shouldRenderInPip;

  public CallParticipantsLayout(@NonNull Context context) {
    super(context);
  }

  public CallParticipantsLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public CallParticipantsLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  void update(@NonNull List<CallParticipant> callParticipants, boolean shouldRenderInPip) {
    this.callParticipants  = callParticipants;
    this.shouldRenderInPip = shouldRenderInPip;
    updateLayout();
  }

  private void updateLayout() {
    if (shouldRenderInPip && Util.hasItems(callParticipants)) {
      updateChildrenCount(1);
      update(0, callParticipants.get(0));
    } else {
      int count = callParticipants.size();
      updateChildrenCount(count);

      for (int i = 0; i < callParticipants.size(); i++) {
        update(i, callParticipants.get(i));
      }
    }
  }

  private void updateChildrenCount(int count) {
    int childCount = getChildCount();
    if (childCount < count) {
      for (int i = childCount; i < count; i++) {
        addCallParticipantView();
      }
    } else if (childCount > count) {
      for (int i = count; i < childCount; i++) {
        removeViewAt(count);
      }
    }
  }

  private void update(int index, @NonNull CallParticipant participant) {
    CallParticipantView callParticipantView = (CallParticipantView) getChildAt(index);
    callParticipantView.setCallParticipant(participant);
    callParticipantView.setRenderInPip(shouldRenderInPip);
    setChildLayoutParams(callParticipantView, index, getChildCount());
  }

  private void addCallParticipantView() {
    View                       view   = LayoutInflater.from(getContext()).inflate(R.layout.call_participant_item, this, false);
    FlexboxLayout.LayoutParams params = (FlexboxLayout.LayoutParams) view.getLayoutParams();

    params.setAlignSelf(AlignItems.STRETCH);
    view.setLayoutParams(params);
    addView(view);
  }

  private void setChildLayoutParams(@NonNull View child, int childPosition, int childCount) {
    FlexboxLayout.LayoutParams params = (FlexboxLayout.LayoutParams) child.getLayoutParams();
    if (childCount < 3) {
      params.setFlexBasisPercent(1f);
    } else {
      if ((childCount % 2) != 0 && childPosition == childCount - 1) {
        params.setFlexBasisPercent(1f);
      } else {
        params.setFlexBasisPercent(0.5f);
      }
    }
    child.setLayoutParams(params);
  }
}
