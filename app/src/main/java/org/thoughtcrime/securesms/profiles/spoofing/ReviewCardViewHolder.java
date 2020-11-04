package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.util.SpanUtil;

class ReviewCardViewHolder extends RecyclerView.ViewHolder {

  private final int             noGroupsInCommonResId;
  private final int             groupsInCommonResId;
  private final TextView        title;
  private final AvatarImageView avatar;
  private final TextView        name;
  private final TextView        subtextLine1;
  private final TextView        subtextLine2;
  private final Button          primaryAction;
  private final Button          secondaryAction;

  public ReviewCardViewHolder(@NonNull View itemView,
                              @StringRes int noGroupsInCommonResId,
                              @PluralsRes int groupsInCommonResId,
                              @NonNull Callbacks callbacks)
  {
    super(itemView);

    this.noGroupsInCommonResId = noGroupsInCommonResId;
    this.groupsInCommonResId   = groupsInCommonResId;
    this.title                 = itemView.findViewById(R.id.card_title);
    this.avatar                = itemView.findViewById(R.id.card_avatar);
    this.name                  = itemView.findViewById(R.id.card_name);
    this.subtextLine1          = itemView.findViewById(R.id.card_subtext_line1);
    this.subtextLine2          = itemView.findViewById(R.id.card_subtext_line2);
    this.primaryAction         = itemView.findViewById(R.id.card_primary_action_button);
    this.secondaryAction       = itemView.findViewById(R.id.card_secondary_action_button);

    itemView.findViewById(R.id.card_tap_target).setOnClickListener(unused -> {
      if (getAdapterPosition() != RecyclerView.NO_POSITION) {
        callbacks.onCardClicked(getAdapterPosition());
      }
    });

    primaryAction.setOnClickListener(unused -> {
      if (getAdapterPosition() != RecyclerView.NO_POSITION) {
        callbacks.onPrimaryActionItemClicked(getAdapterPosition());
      }
    });

    secondaryAction.setOnClickListener(unused -> {
      if (getAdapterPosition() != RecyclerView.NO_POSITION) {
        callbacks.onSecondaryActionItemClicked(getAdapterPosition());
      }
    });
  }

  void bind(@NonNull ReviewCard reviewCard) {
    Context context = itemView.getContext();

    avatar.setAvatar(reviewCard.getReviewRecipient());
    name.setText(reviewCard.getReviewRecipient().getDisplayName(context));
    title.setText(getTitleResId(reviewCard.getCardType()));

    switch (reviewCard.getCardType()) {
      case MEMBER:
      case REQUEST:
        setNonContactSublines(context, reviewCard);
        break;
      case YOUR_CONTACT:
        subtextLine1.setText(reviewCard.getReviewRecipient().getE164().orNull());
        subtextLine2.setText(getGroupsInCommon(reviewCard.getInCommonGroupsCount()));
        break;
      default:
        throw new AssertionError();
    }

    setActions(reviewCard);
  }

  private void setNonContactSublines(@NonNull Context context, @NonNull ReviewCard reviewCard) {
    subtextLine1.setText(getGroupsInCommon(reviewCard.getInCommonGroupsCount()));

    if (reviewCard.getNameChange() != null) {
      subtextLine2.setText(SpanUtil.italic(context.getString(R.string.ReviewCard__recently_changed,
                                                             reviewCard.getNameChange().getPrevious(),
                                                             reviewCard.getNameChange().getNew())));
    }
  }

  private void setActions(@NonNull ReviewCard reviewCard) {
    setAction(reviewCard.getPrimaryAction(), primaryAction);
    setAction(reviewCard.getSecondaryAction(), secondaryAction);
  }

  private String getGroupsInCommon(int groupsInCommon) {
    if (groupsInCommon == 0) {
      return itemView.getContext().getString(noGroupsInCommonResId);
    } else {
      return itemView.getResources().getQuantityString(groupsInCommonResId, groupsInCommon, groupsInCommon);
    }
  }

  private static void setAction(@Nullable ReviewCard.Action action, @NonNull Button actionButton) {
    if (action != null) {
      actionButton.setText(getActionLabelResId(action));
      actionButton.setVisibility(View.VISIBLE);
    } else {
      actionButton.setVisibility(View.GONE);
    }
  }

  interface Callbacks {
    void onCardClicked(int position);
    void onPrimaryActionItemClicked(int position);
    void onSecondaryActionItemClicked(int position);
  }

  private static @StringRes int getTitleResId(@NonNull ReviewCard.CardType cardType) {
    switch (cardType) {
      case MEMBER:
        return R.string.ReviewCard__member;
      case REQUEST:
        return R.string.ReviewCard__request;
      case YOUR_CONTACT:
        return R.string.ReviewCard__your_contact;
      default:
        throw new IllegalArgumentException("Unsupported card type " + cardType);
    }
  }

  private static @StringRes int getActionLabelResId(@NonNull ReviewCard.Action action) {
    switch (action) {
      case UPDATE_CONTACT:
        return R.string.ReviewCard__update_contact;
      case DELETE:
        return R.string.ReviewCard__delete;
      case BLOCK:
        return R.string.ReviewCard__block;
      case REMOVE_FROM_GROUP:
        return R.string.ReviewCard__remove_from_group;
      default:
        throw new IllegalArgumentException("Unsupported action: " + action);
    }
  }
}
