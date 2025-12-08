package org.thoughtcrime.securesms.components.webrtc;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CallParticipantsListUpdatePopupWindow extends PopupWindow implements DefaultLifecycleObserver {

  private static final long DURATION = TimeUnit.SECONDS.toMillis(5);

  private final ViewGroup       parent;
  private final AvatarImageView avatarImageView;
  private final BadgeImageView  badgeImageView;
  private final TextView        descriptionTextView;
  private final Handler         handler;

  private final Set<CallParticipantListUpdate.Wrapper> pendingAdditions = new HashSet<>();
  private final Set<CallParticipantListUpdate.Wrapper> pendingRemovals  = new HashSet<>();

  private boolean isEnabled = true;

  public CallParticipantsListUpdatePopupWindow(@NonNull ViewGroup parent) {
    super(LayoutInflater.from(parent.getContext()).inflate(R.layout.call_participant_list_update, parent, false),
                                                           ViewGroup.LayoutParams.MATCH_PARENT,
                                                           ViewGroup.LayoutParams.WRAP_CONTENT);

    this.parent              = parent;
    this.avatarImageView     = getContentView().findViewById(R.id.avatar);
    this.badgeImageView      = getContentView().findViewById(R.id.badge);
    this.descriptionTextView = getContentView().findViewById(R.id.description);
    this.handler             = new Handler(Looper.getMainLooper());

    setOnDismissListener(this::showPending);
    getContentView().setOnClickListener(v -> dismiss());
    setAnimationStyle(R.style.PopupAnimation);
  }

  public void addCallParticipantListUpdate(@NonNull CallParticipantListUpdate update) {
    pendingAdditions.addAll(update.getAdded());
    pendingAdditions.removeAll(update.getRemoved());

    pendingRemovals.addAll(update.getRemoved());
    pendingRemovals.removeAll(update.getAdded());

    if (!isShowing()) {
      showPending();
    }
  }

  public void setEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;

    if (!isEnabled) {
      dismiss();
    }
  }

  @Override
  public void onDestroy(@NonNull LifecycleOwner owner) {
    handler.removeCallbacksAndMessages(null);
    setOnDismissListener(null);
    dismiss();
  }

  private void showPending() {
    if (!pendingAdditions.isEmpty()) {
      showAdditions();
    } else if (!pendingRemovals.isEmpty()) {
      showRemovals();
    }
  }

  private void showAdditions() {
    setAvatar(getNextRecipient(pendingAdditions.iterator()));
    setDescription(pendingAdditions, true);
    pendingAdditions.clear();
    show();
  }

  private void showRemovals() {
    setAvatar(getNextRecipient(pendingRemovals.iterator()));
    setDescription(pendingRemovals, false);
    pendingRemovals.clear();
    show();
  }

  private void show() {
    if (!isEnabled || !parent.isAttachedToWindow()) {
      return;
    }

    showAtLocation(parent, Gravity.TOP | Gravity.START, 0, 0);
    measureChild();
    update();
    handler.postDelayed(this::dismiss, DURATION);
  }

  private void measureChild() {
    getContentView().measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                             View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
  }

  private void setAvatar(@Nullable Recipient recipient) {
    avatarImageView.setAvatarUsingProfile(recipient);
    badgeImageView.setBadgeFromRecipient(recipient);
    avatarImageView.setVisibility(recipient == null ? View.GONE : View.VISIBLE);
  }

  private void setDescription(@NonNull Set<CallParticipantListUpdate.Wrapper> wrappers, boolean isAdded) {
    if (wrappers.isEmpty()) {
      descriptionTextView.setText("");
    } else {
      setDescriptionForRecipients(wrappers, isAdded);
    }
  }

  private void setDescriptionForRecipients(@NonNull Set<CallParticipantListUpdate.Wrapper> recipients, boolean isAdded) {
    descriptionTextView.setText(getDescriptionForRecipients(getContentView().getContext(), recipients, isAdded));
  }

  public static @NonNull String getDescriptionForRecipients(@NonNull Context context, @NonNull Set<CallParticipantListUpdate.Wrapper> recipients, boolean isAdded) {
    Iterator<CallParticipantListUpdate.Wrapper> iterator = recipients.iterator();
    String                                      description;

    switch (recipients.size()) {
      case 0:
        throw new IllegalArgumentException("Recipients must contain 1 or more entries");
      case 1:
        description = context.getString(getOneMemberDescriptionResourceId(isAdded), getNextDisplayName(context, iterator));
        break;
      case 2:
        description = context.getString(getTwoMemberDescriptionResourceId(isAdded), getNextDisplayName(context, iterator), getNextDisplayName(context, iterator));
        break;
      case 3:
        description = context.getString(getThreeMemberDescriptionResourceId(isAdded), getNextDisplayName(context, iterator), getNextDisplayName(context, iterator), getNextDisplayName(context, iterator));
        break;
      default:
        description = context.getResources().getQuantityString(getManyMemberDescriptionResourceId(isAdded), recipients.size() - 2, getNextDisplayName(context, iterator), getNextDisplayName(context, iterator), recipients.size() - 2);
    }

    return description;
  }

  private @NonNull Recipient getNextRecipient(@NonNull Iterator<CallParticipantListUpdate.Wrapper> wrapperIterator) {
    return wrapperIterator.next().getCallParticipant().getRecipient();
  }

  private static @NonNull String getNextDisplayName(@NonNull Context context, @NonNull Iterator<CallParticipantListUpdate.Wrapper> wrapperIterator) {
    CallParticipantListUpdate.Wrapper wrapper   = wrapperIterator.next();

    return wrapper.getCallParticipant().getRecipientDisplayName(context);
  }

  private static @StringRes int getOneMemberDescriptionResourceId(boolean isAdded) {
    if (isAdded) {
      return R.string.CallParticipantsListUpdatePopupWindow__s_joined;
    } else {
      return R.string.CallParticipantsListUpdatePopupWindow__s_left;
    }
  }

  private static @StringRes int getTwoMemberDescriptionResourceId(boolean isAdded) {
    if (isAdded) {
      return R.string.CallParticipantsListUpdatePopupWindow__s_and_s_joined;
    } else {
      return R.string.CallParticipantsListUpdatePopupWindow__s_and_s_left;
    }
  }

  private static @StringRes int getThreeMemberDescriptionResourceId(boolean isAdded) {
    if (isAdded) {
      return R.string.CallParticipantsListUpdatePopupWindow__s_s_and_s_joined;
    } else {
      return R.string.CallParticipantsListUpdatePopupWindow__s_s_and_s_left;
    }
  }

  private static @PluralsRes int getManyMemberDescriptionResourceId(boolean isAdded) {
    if (isAdded) {
      return R.plurals.CallParticipantsListUpdatePopupWindow__s_s_and_d_others_joined;
    } else {
      return R.plurals.CallParticipantsListUpdatePopupWindow__s_s_and_d_others_left;
    }
  }
}
