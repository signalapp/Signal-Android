package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;

public final class EditAboutViewModel extends ViewModel {

  private final ManageProfileRepository    repository;
  private final BehaviorSubject<SaveState> saveState;
  private final PublishSubject<Event>      events;

  public EditAboutViewModel() {
    this.repository = new ManageProfileRepository();
    this.saveState  = BehaviorSubject.createDefault(SaveState.IDLE);
    this.events     = PublishSubject.create();
  }

  @NonNull Observable<SaveState> getSaveState() {
    return saveState.observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull Observable<Event> getEvents() {
    return events.observeOn(AndroidSchedulers.mainThread());
  }

  void onSaveClicked(@NonNull Context context, @NonNull String about, @NonNull String emoji) {
    saveState.onNext(SaveState.IN_PROGRESS);
    repository.setAbout(context, about, emoji, result -> {
      switch (result) {
        case SUCCESS:
          saveState.onNext(SaveState.DONE);
          break;
        case FAILURE_NETWORK:
          saveState.onNext(SaveState.IDLE);
          events.onNext(Event.NETWORK_FAILURE);
          break;
      }
    });
  }

  enum SaveState {
    IDLE, IN_PROGRESS, DONE
  }

  enum Event {
    NETWORK_FAILURE
  }
}
