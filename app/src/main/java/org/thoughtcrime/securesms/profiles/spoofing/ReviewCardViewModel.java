package org.thoughtcrime.securesms.profiles.spoofing;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;
import java.util.Objects;

public class ReviewCardViewModel extends ViewModel {

  private final ReviewCardRepository                   repository;
  private final MutableLiveData<List<ReviewRecipient>> reviewRecipients;
  private final LiveData<List<ReviewCard>>             reviewCards;
  private final SingleLiveEvent<Event>                 reviewEvents;
  private final boolean                                isGroupThread;

  public ReviewCardViewModel(@NonNull ReviewCardRepository repository, @Nullable GroupId groupId) {
    final LiveData<Boolean> isSelfGroupAdmin;

    if (groupId != null) {
      LiveGroup liveGroup = new LiveGroup(groupId);
      isSelfGroupAdmin = liveGroup.getRecipientIsAdmin(Recipient.self().getId());
    } else {
      isSelfGroupAdmin = new DefaultValueLiveData<>(false);
    }

    this.isGroupThread    = groupId != null;
    this.repository       = repository;
    this.reviewRecipients = new MutableLiveData<>();

    LiveData<Pair<Boolean, List<ReviewRecipient>>> adminStatusAndReviewRecipients = LiveDataUtil.combineLatest(isSelfGroupAdmin, reviewRecipients, Pair::new);

    this.reviewCards      = LiveDataUtil.mapAsync(adminStatusAndReviewRecipients, pair -> transformReviewRecipients(pair.first, pair.second));
    this.reviewEvents     = new SingleLiveEvent<>();

    repository.loadRecipients(new OnRecipientsLoadedListener());
  }

  LiveData<List<ReviewCard>> getReviewCards() {
    return reviewCards;
  }

  LiveData<Event> getReviewEvents() {
    return reviewEvents;
  }

  public void act(@NonNull ReviewCard card, @NonNull ReviewCard.Action action) {
    if (card.getPrimaryAction() == action || card.getSecondaryAction() == action) {
      performAction(card, action);
    } else {
      throw new IllegalArgumentException("Cannot perform " + action + " on review card.");
    }
  }

  private void performAction(@NonNull ReviewCard card, @NonNull ReviewCard.Action action) {
    switch (action) {
      case BLOCK:
        repository.block(card, () -> reviewEvents.postValue(Event.DISMISS));
        break;
      case DELETE:
        repository.delete(card, () -> reviewEvents.postValue(Event.DISMISS));
        break;
      case REMOVE_FROM_GROUP:
        repository.removeFromGroup(card, new OnRemoveFromGroupListener());
        break;
      default:
        throw new IllegalArgumentException("Unsupported action: " + action);
    }
  }

  @WorkerThread
  private @NonNull List<ReviewCard> transformReviewRecipients(boolean isSelfGroupAdmin, @NonNull List<ReviewRecipient> reviewRecipients) {
    return Stream.of(reviewRecipients)
                 .filter(r -> repository.loadGroupsInCommonCount(r) > 0)
                 .map(r -> new ReviewCard(r,
                                          repository.loadGroupsInCommonCount(r) - (isGroupThread ? 1 : 0),
                                          getCardType(r),
                                          getPrimaryAction(r, isSelfGroupAdmin),
                                          getSecondaryAction(r)))
                 .toList();

  }

  private @NonNull ReviewCard.CardType getCardType(@NonNull ReviewRecipient reviewRecipient) {
    if (reviewRecipient.getRecipient().isSystemContact()) {
      return ReviewCard.CardType.YOUR_CONTACT;
    } else if (isGroupThread) {
      return ReviewCard.CardType.MEMBER;
    } else {
      return ReviewCard.CardType.REQUEST;
    }
  }

  private @NonNull ReviewCard.Action getPrimaryAction(@NonNull ReviewRecipient reviewRecipient, boolean isSelfGroupAdmin) {
    if (reviewRecipient.getRecipient().isSystemContact()) {
      return ReviewCard.Action.UPDATE_CONTACT;
    } else if (isGroupThread && isSelfGroupAdmin) {
      return ReviewCard.Action.REMOVE_FROM_GROUP;
    } else {
      return ReviewCard.Action.BLOCK;
    }
  }

  private @Nullable ReviewCard.Action getSecondaryAction(@NonNull ReviewRecipient reviewRecipient) {
    if (reviewRecipient.getRecipient().isSystemContact()) {
      return null;
    } else if (isGroupThread) {
      return null;
    } else {
      return ReviewCard.Action.DELETE;
    }
  }

  private class OnRecipientsLoadedListener implements ReviewCardRepository.OnRecipientsLoadedListener {
    @Override
    public void onRecipientsLoaded(@NonNull List<ReviewRecipient> recipients) {
      if (recipients.size() < 2) {
        reviewEvents.postValue(Event.DISMISS);
      } else {
        reviewRecipients.postValue(recipients);
      }
    }

    @Override
    public void onRecipientsLoadFailed() {
      reviewEvents.postValue(Event.DISMISS);
    }
  }

  private class OnRemoveFromGroupListener implements ReviewCardRepository.OnRemoveFromGroupListener {
    @Override
    public void onActionCompleted() {
      repository.loadRecipients(new OnRecipientsLoadedListener());
    }

    @Override
    public void onActionFailed() {
      reviewEvents.postValue(Event.REMOVE_FAILED);
    }
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final ReviewCardRepository repository;
    private final GroupId              groupId;

    public Factory(@NonNull ReviewCardRepository repository, @Nullable GroupId groupId) {
      this.repository = repository;
      this.groupId    = groupId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ReviewCardViewModel(repository, groupId)));
    }
  }

  public enum Event {
    DISMISS,
    REMOVE_FAILED
  }
}
