package org.thoughtcrime.securesms.profiles.spoofing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Represents a card showing user details for a recipient under review.
 * <p>
 * See {@link ReviewCardViewHolder} for usage.
 */
class ReviewCard {

  private final ReviewRecipient reviewRecipient;
  private final int             inCommonGroupsCount;
  private final CardType        cardType;
  private final Action          primaryAction;
  private final Action          secondaryAction;

  ReviewCard(@NonNull ReviewRecipient reviewRecipient,
             int inCommonGroupsCount,
             @NonNull CardType cardType,
             @Nullable Action primaryAction,
             @Nullable Action secondaryAction)
  {
    this.reviewRecipient     = reviewRecipient;
    this.inCommonGroupsCount = inCommonGroupsCount;
    this.cardType            = cardType;
    this.primaryAction       = primaryAction;
    this.secondaryAction     = secondaryAction;
  }

  @NonNull Recipient getReviewRecipient() {
    return reviewRecipient.getRecipient();
  }

  @NonNull CardType getCardType() {
    return cardType;
  }

  int getInCommonGroupsCount() {
    return inCommonGroupsCount;
  }

  @Nullable ProfileChangeDetails.StringChange getNameChange() {
    if (reviewRecipient.getProfileChangeDetails() == null || reviewRecipient.getProfileChangeDetails().profileNameChange == null) {
      return null;
    } else {
      return reviewRecipient.getProfileChangeDetails().profileNameChange;
    }
  }

  @Nullable Action getPrimaryAction() {
    return primaryAction;
  }

  @Nullable Action getSecondaryAction() {
    return secondaryAction;
  }

  enum CardType {
    MEMBER,
    REQUEST,
    YOUR_CONTACT
  }

  enum Action {
    UPDATE_CONTACT,
    DELETE,
    BLOCK,
    REMOVE_FROM_GROUP
  }
}
