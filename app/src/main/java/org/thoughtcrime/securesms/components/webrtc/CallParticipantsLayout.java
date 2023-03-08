package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.card.MaterialCardView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Collections;
import java.util.List;

/**
 * Can dynamically render a collection of call participants, adjusting their
 * sizing and layout depending on the total number of participants.
 */
public class CallParticipantsLayout extends FlexboxLayout {

  private static final int MULTIPLE_PARTICIPANT_SPACING = ViewUtil.dpToPx(3);
  private static final int CORNER_RADIUS                = ViewUtil.dpToPx(10);

  private List<CallParticipant> callParticipants   = Collections.emptyList();
  private CallParticipant       focusedParticipant = null;
  private boolean               shouldRenderInPip;
  private boolean               isPortrait;
  private boolean               isIncomingRing;
  private int                   navBarBottomInset;
  private LayoutStrategy        layoutStrategy;

  public CallParticipantsLayout(@NonNull Context context) {
    super(context);
  }

  public CallParticipantsLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public CallParticipantsLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  void update(@NonNull List<CallParticipant> callParticipants,
              @NonNull CallParticipant focusedParticipant,
              boolean shouldRenderInPip,
              boolean isPortrait,
              boolean isIncomingRing,
              int navBarBottomInset,
              @NonNull LayoutStrategy layoutStrategy)
  {
    this.callParticipants   = callParticipants;
    this.focusedParticipant = focusedParticipant;
    this.shouldRenderInPip  = shouldRenderInPip;
    this.isPortrait         = isPortrait;
    this.isIncomingRing     = isIncomingRing;
    this.navBarBottomInset  = navBarBottomInset;
    this.layoutStrategy     = layoutStrategy;

    setFlexDirection(layoutStrategy.getFlexDirection());
    updateLayout();
  }

  private void updateLayout() {
    int previousChildCount = getChildCount();

    if (shouldRenderInPip && Util.hasItems(callParticipants)) {
      updateChildrenCount(1);
      update(0, 1, focusedParticipant);
    } else {
      int count = callParticipants.size();
      updateChildrenCount(count);

      for (int i = 0; i < count; i++) {
        update(i, count, callParticipants.get(i));
      }
    }

    if (previousChildCount != getChildCount()) {
      updateMarginsForLayout();
    }
  }

  private void updateMarginsForLayout() {
    MarginLayoutParams layoutParams = (MarginLayoutParams) getLayoutParams();
    if (callParticipants.size() > 1 && !shouldRenderInPip) {
      layoutParams.setMargins(MULTIPLE_PARTICIPANT_SPACING, ViewUtil.getStatusBarHeight(this), MULTIPLE_PARTICIPANT_SPACING, 0);
    } else {
      layoutParams.setMargins(0, 0, 0, 0);
    }
    setLayoutParams(layoutParams);
  }

  private void updateChildrenCount(int count) {
    int childCount = getChildCount();
    if (childCount < count) {
      for (int i = childCount; i < count; i++) {
        addCallParticipantView();
      }
    } else if (childCount > count) {
      for (int i = count; i < childCount; i++) {
        CallParticipantView callParticipantView = getChildAt(count).findViewById(R.id.group_call_participant);
        callParticipantView.releaseRenderer();
        removeViewAt(count);
      }
    }
  }

  private void update(int index, int count, @NonNull CallParticipant participant) {
    View                view                = getChildAt(index);
    MaterialCardView    cardView            = view.findViewById(R.id.group_call_participant_card_wrapper);
    CallParticipantView callParticipantView = view.findViewById(R.id.group_call_participant);

    callParticipantView.setCallParticipant(participant);
    callParticipantView.setRenderInPip(shouldRenderInPip);
    layoutStrategy.setChildScaling(participant, callParticipantView, isPortrait, count);

    if (count > 1) {
      view.setPadding(MULTIPLE_PARTICIPANT_SPACING, MULTIPLE_PARTICIPANT_SPACING, MULTIPLE_PARTICIPANT_SPACING, MULTIPLE_PARTICIPANT_SPACING);
      cardView.setRadius(CORNER_RADIUS);
      callParticipantView.setBottomInset(0);
    } else {
      view.setPadding(0, 0, 0, 0);
      cardView.setRadius(0);
      callParticipantView.setBottomInset(navBarBottomInset);
    }

    if (isIncomingRing) {
      callParticipantView.hideAvatar();
    } else {
      callParticipantView.showAvatar();
    }

    if (count > 2) {
      callParticipantView.useSmallAvatar();
    } else {
      callParticipantView.useLargeAvatar();
    }

    layoutStrategy.setChildLayoutParams(view, index, getChildCount());
  }

  private void addCallParticipantView() {
    View                       view   = LayoutInflater.from(getContext()).inflate(R.layout.group_call_participant_item, this, false);
    FlexboxLayout.LayoutParams params = (FlexboxLayout.LayoutParams) view.getLayoutParams();

    params.setAlignSelf(AlignItems.STRETCH);
    view.setLayoutParams(params);
    addView(view);
  }

  public interface LayoutStrategy {
    int getFlexDirection();

    void setChildScaling(@NonNull CallParticipant callParticipant,
                         @NonNull CallParticipantView callParticipantView,
                         boolean isPortrait,
                         int childCount);

    void setChildLayoutParams(@NonNull View child, int childPosition, int childCount);
  }
}
