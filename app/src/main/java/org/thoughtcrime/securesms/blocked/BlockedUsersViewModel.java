package org.thoughtcrime.securesms.blocked;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class BlockedUsersViewModel extends ViewModel {

  private final BlockedUsersRepository   repository;
  private final Subject<List<Recipient>> recipients = BehaviorSubject.create();
  private final Subject<Event>           events     = PublishSubject.create();

  private BlockedUsersViewModel(@NonNull BlockedUsersRepository repository) {
    this.repository  = repository;

    loadRecipients();
   }

  public Observable<List<Recipient>> getRecipients() {
    return recipients.observeOn(AndroidSchedulers.mainThread());
  }

  public Observable<Event> getEvents() {
    return events.observeOn(AndroidSchedulers.mainThread());
  }

  void block(@NonNull RecipientId recipientId) {
    repository.block(recipientId,
                     () -> {
                       loadRecipients();
                       events.onNext(new Event(EventType.BLOCK_SUCCEEDED, Recipient.resolved(recipientId)));
                     },
                     () -> events.onNext(new Event(EventType.BLOCK_FAILED, Recipient.resolved(recipientId))));
  }

  void createAndBlock(@NonNull String number) {
    repository.createAndBlock(number, () -> {
      loadRecipients();
      events.onNext(new Event(EventType.BLOCK_SUCCEEDED, number));
    });
  }

  void unblock(@NonNull RecipientId recipientId) {
    repository.unblock(recipientId, () -> {
      loadRecipients();
      events.onNext(new Event(EventType.UNBLOCK_SUCCEEDED, Recipient.resolved(recipientId)));
    });
  }

  private void loadRecipients() {
    repository.getBlocked(recipients::onNext);
  }

  enum EventType {
    BLOCK_SUCCEEDED,
    BLOCK_FAILED,
    UNBLOCK_SUCCEEDED
  }

  public static final class Event {

    private final EventType eventType;
    private final Recipient recipient;
    private final String    number;

    private Event(@NonNull EventType eventType, @NonNull Recipient recipient) {
      this.eventType = eventType;
      this.recipient = recipient;
      this.number    = null;
    }

    private Event(@NonNull EventType eventType, @NonNull String number) {
      this.eventType = eventType;
      this.recipient = null;
      this.number    = number;
    }

    public @Nullable Recipient getRecipient() {
      return recipient;
    }

    public @Nullable String getNumber() {
      return number;
    }

    public @NonNull EventType getEventType() {
      return eventType;
    }
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final BlockedUsersRepository repository;

    public Factory(@NonNull BlockedUsersRepository repository) {
      this.repository = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new BlockedUsersViewModel(repository)));
    }
  }
}
