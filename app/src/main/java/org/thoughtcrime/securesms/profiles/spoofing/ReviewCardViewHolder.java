package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.databinding.ReviewCardBinding;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ReviewCardViewHolder extends RecyclerView.ViewHolder {

  private final int                             noGroupsInCommonResId;
  private final int                             groupsInCommonResId;
  private final ReviewCardBinding               binding;
  private final List<Pair<TextView, ImageView>> subtextGroups;
  private final Runnable                        onSignalConnectionClicked;

  public ReviewCardViewHolder(@NonNull View itemView,
                              @StringRes int noGroupsInCommonResId,
                              @PluralsRes int groupsInCommonResId,
                              @NonNull Callbacks callbacks)
  {
    super(itemView);

    this.noGroupsInCommonResId = noGroupsInCommonResId;
    this.groupsInCommonResId   = groupsInCommonResId;
    this.binding               = ReviewCardBinding.bind(itemView);

    this.subtextGroups = Arrays.asList(
        Pair.create(binding.cardSubtextLine1, binding.cardSubtextIcon1),
        Pair.create(binding.cardSubtextLine2, binding.cardSubtextIcon2),
        Pair.create(binding.cardSubtextLine3, binding.cardSubtextIcon3),
        Pair.create(binding.cardSubtextLine4, binding.cardSubtextIcon4)
    );

    itemView.findViewById(R.id.card_tap_target).setOnClickListener(unused -> {
      if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
        callbacks.onCardClicked(getBindingAdapterPosition());
      }
    });

    binding.cardPrimaryActionButton.setOnClickListener(unused -> {
      if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
        callbacks.onPrimaryActionItemClicked(getBindingAdapterPosition());
      }
    });

    binding.cardSecondaryActionButton.setOnClickListener(unused -> {
      if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
        callbacks.onSecondaryActionItemClicked(getBindingAdapterPosition());
      }
    });

    onSignalConnectionClicked = callbacks::onSignalConnectionClicked;
  }

  void bind(@NonNull ReviewCard reviewCard) {
    Context context = itemView.getContext();

    binding.cardAvatar.setAvatarUsingProfile(reviewCard.getReviewRecipient());

    String name = reviewCard.getReviewRecipient().isSelf()
                  ? context.getString(R.string.AboutSheet__you)
                  : reviewCard.getReviewRecipient().getDisplayName(context);

    binding.cardName.setText(name);

    List<ReviewTextRow> rows = switch (reviewCard.getCardType()) {
      case MEMBER, REQUEST -> getNonContactSublines(reviewCard);
      case YOUR_CONTACT -> getContactSublines(reviewCard);
    };

    presentReviewTextRows(rows, context, reviewCard);

    setActions(reviewCard);
  }

  private List<ReviewTextRow> getNonContactSublines(@NonNull ReviewCard reviewCard) {
    List<ReviewTextRow> reviewTextRows = new ArrayList<>(subtextGroups.size());

    if (reviewCard.getReviewRecipient().isProfileSharing() && !reviewCard.getReviewRecipient().isSelf()) {
      reviewTextRows.add(ReviewTextRow.SIGNAL_CONNECTION);
    }

    if (reviewCard.getReviewRecipient().isSystemContact()) {
      reviewTextRows.add(ReviewTextRow.SYSTEM_CONTACTS);
    }

    if (reviewCard.getNameChange() != null) {
      reviewTextRows.add(ReviewTextRow.RECENTLY_CHANGED);
    }

    reviewTextRows.add(ReviewTextRow.GROUPS_IN_COMMON);

    return reviewTextRows;
  }

  private List<ReviewTextRow> getContactSublines(@NonNull ReviewCard reviewCard) {
    List<ReviewTextRow> reviewTextRows = new ArrayList<>(subtextGroups.size());

    if (reviewCard.getReviewRecipient().isProfileSharing() && !reviewCard.getReviewRecipient().isSelf()) {
      reviewTextRows.add(ReviewTextRow.SIGNAL_CONNECTION);
    }

    if (reviewCard.getReviewRecipient().isSystemContact()) {
      reviewTextRows.add(ReviewTextRow.SYSTEM_CONTACTS);
    }

    if (reviewCard.getReviewRecipient().hasE164() && reviewCard.getReviewRecipient().shouldShowE164()) {
      reviewTextRows.add(ReviewTextRow.PHONE_NUMBER);
    }

    reviewTextRows.add(ReviewTextRow.GROUPS_IN_COMMON);

    return reviewTextRows;
  }

  private void presentReviewTextRows(@NonNull List<ReviewTextRow> reviewTextRows, @NonNull Context context, @NonNull ReviewCard reviewCard) {

    for (Pair<TextView, ImageView> group : subtextGroups) {
      setVisibility(View.GONE, group.first, group.second);
    }

    for (int i = 0; i < Math.min(reviewTextRows.size(), subtextGroups.size()); i++) {
      ReviewTextRow             row   = reviewTextRows.get(i);
      Pair<TextView, ImageView> group = subtextGroups.get(i);

      setVisibility(View.VISIBLE, group.first, group.second);

      switch (row) {
        case SIGNAL_CONNECTION -> presentSignalConnection(group.first, group.second, context, reviewCard);
        case PHONE_NUMBER -> presentPhoneNumber(group.first, group.second, reviewCard);
        case RECENTLY_CHANGED -> presentRecentlyChanged(group.first, group.second, context, reviewCard);
        case GROUPS_IN_COMMON -> presentGroupsInCommon(group.first, group.second, reviewCard);
        case SYSTEM_CONTACTS -> presentSystemContacts(group.first, group.second, context, reviewCard);
      }
    }
  }

  private void presentSignalConnection(@NonNull TextView line, @NonNull ImageView icon, @NonNull Context context, @NonNull ReviewCard reviewCard) {
    Preconditions.checkArgument(reviewCard.getReviewRecipient().isProfileSharing());

    Drawable chevron = ContextCompat.getDrawable(context, R.drawable.symbol_chevron_right_24);
    Preconditions.checkNotNull(chevron);
    chevron.setTint(ContextCompat.getColor(context, R.color.core_grey_45));

    SpannableStringBuilder builder = new SpannableStringBuilder(context.getString(R.string.AboutSheet__signal_connection));
    SpanUtil.appendCenteredImageSpan(builder, chevron, 20, 20);

    icon.setImageResource(R.drawable.symbol_connections_compact_16);
    line.setText(builder);
    line.setOnClickListener(v -> onSignalConnectionClicked.run());
  }

  private void presentPhoneNumber(@NonNull TextView line, @NonNull ImageView icon, @NonNull ReviewCard reviewCard) {
    icon.setImageResource(R.drawable.symbol_phone_compact_16);
    line.setText(reviewCard.getReviewRecipient().requireE164());
    line.setOnClickListener(null);
    line.setClickable(false);
  }

  private void presentRecentlyChanged(@NonNull TextView line, @NonNull ImageView icon, @NonNull Context context, @NonNull ReviewCard reviewCard) {
    Preconditions.checkNotNull(reviewCard.getNameChange());

    icon.setImageResource(R.drawable.symbol_person_compact_16);
    line.setText(context.getString(R.string.ReviewCard__s_recently_changed,
                                                       reviewCard.getReviewRecipient().getShortDisplayName(context),
                                                       reviewCard.getNameChange().previous,
                                                       reviewCard.getNameChange().newValue));
    line.setOnClickListener(null);
    line.setClickable(false);
  }

  private void presentGroupsInCommon(@NonNull TextView line, @NonNull ImageView icon, @NonNull ReviewCard reviewCard) {
    icon.setImageResource(R.drawable.symbol_group_compact_16);
    line.setText(getGroupsInCommon(reviewCard.getInCommonGroupsCount()));
    line.setOnClickListener(null);
    line.setClickable(false);
  }

  private void presentSystemContacts(@NonNull TextView line, @NonNull ImageView icon, @NonNull Context context, @NonNull ReviewCard reviewCard) {
    icon.setImageResource(R.drawable.symbol_person_circle_compat_16);
    line.setText(context.getString(R.string.ReviewCard__s_is_in_your_system_contacts, reviewCard.getReviewRecipient().getShortDisplayName(context)));
    line.setOnClickListener(null);
    line.setClickable(false);
  }

  private void setVisibility(int visibility, View... views) {
    for (View view : views) {
      view.setVisibility(visibility);
    }
  }

  private void setActions(@NonNull ReviewCard reviewCard) {
    if (reviewCard.getReviewRecipient().isSelf()) {
      setAction(null, binding.cardPrimaryActionButton);
      setAction(null, binding.cardSecondaryActionButton);
    } else {
      setAction(reviewCard.getPrimaryAction(), binding.cardPrimaryActionButton);
      setAction(reviewCard.getSecondaryAction(), binding.cardSecondaryActionButton);
    }
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

    void onSignalConnectionClicked();
  }

  private static @StringRes int getActionLabelResId(@NonNull ReviewCard.Action action) {
    return switch (action) {
      case UPDATE_CONTACT -> R.string.ReviewCard__update_contact;
      case DELETE -> R.string.ReviewCard__delete;
      case BLOCK -> R.string.ReviewCard__block;
      case REMOVE_FROM_GROUP -> R.string.ReviewCard__remove_from_group;
    };
  }

  private enum ReviewTextRow {
    SIGNAL_CONNECTION,
    PHONE_NUMBER,
    RECENTLY_CHANGED,
    GROUPS_IN_COMMON,
    SYSTEM_CONTACTS
  }
}
