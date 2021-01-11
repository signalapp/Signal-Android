package org.thoughtcrime.securesms.recipients;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

public final class LiveRecipient {

  private static final String TAG = Log.tag(LiveRecipient.class);

  private final Context                       context;
  private final MutableLiveData<Recipient>    liveData;
  private final LiveData<Recipient>           observableLiveData;
  private final Set<RecipientForeverObserver> observers;
  private final Observer<Recipient>           foreverObserver;
  private final AtomicReference<Recipient>    recipient;
  private final RecipientDatabase             recipientDatabase;
  private final GroupDatabase                 groupDatabase;
  private final MutableLiveData<Object>       refreshForceNotify;

  LiveRecipient(@NonNull Context context, @NonNull MutableLiveData<Recipient> liveData, @NonNull Recipient defaultRecipient) {
    this.context           = context.getApplicationContext();
    this.liveData          = liveData;
    this.recipient         = new AtomicReference<>(defaultRecipient);
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    this.observers         = new CopyOnWriteArraySet<>();
    this.foreverObserver   = recipient -> {
      for (RecipientForeverObserver o : observers) {
        o.onRecipientChanged(recipient);
      }
    };
    this.refreshForceNotify = new MutableLiveData<>(System.currentTimeMillis());
    this.observableLiveData = LiveDataUtil.combineLatest(LiveDataUtil.distinctUntilChanged(liveData, Recipient::hasSameContent),
                                                         refreshForceNotify,
                                                         (recipient, force) -> recipient);
  }

  public @NonNull RecipientId getId() {
    return recipient.get().getId();
  }

  /**
   * @return A recipient that may or may not be fully-resolved.
   */
  public @NonNull Recipient get() {
    return recipient.get();
  }

  /**
   * Watch the recipient for changes. The callback will only be invoked if the provided lifecycle is
   * in a valid state. No need to remove the observer. If you do wish to remove the observer (if,
   * for instance, you wish to remove the listener before the end of the owner's lifecycle), you can
   * use {@link #removeObservers(LifecycleOwner)}.
   */
  public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<Recipient> observer) {
    Util.postToMain(() -> observableLiveData.observe(owner, observer));
  }

  /**
   * Removes all observers of this data registered for the given LifecycleOwner.
   */
  public void removeObservers(@NonNull LifecycleOwner owner) {
    Util.runOnMain(() -> observableLiveData.removeObservers(owner));
  }

  /**
   * Watch the recipient for changes. The callback could be invoked at any time. You MUST call
   * {@link #removeForeverObserver(RecipientForeverObserver)} when finished. You should use
   * {@link #observe(LifecycleOwner, Observer<Recipient>)} if possible, as it is lifecycle-safe.
   */
  public void observeForever(@NonNull RecipientForeverObserver observer) {
    Util.postToMain(() -> {
      if (observers.isEmpty()) {
        observableLiveData.observeForever(foreverObserver);
      }
      observers.add(observer);
    });
  }

  /**
   * Unsubscribes the provided {@link RecipientForeverObserver} from future changes.
   */
  public void removeForeverObserver(@NonNull RecipientForeverObserver observer) {
    Util.postToMain(() -> {
      observers.remove(observer);

      if (observers.isEmpty()) {
        observableLiveData.removeObserver(foreverObserver);
      }
    });
  }

  /**
   * @return A fully-resolved version of the recipient. May require reading from disk.
   */
  @WorkerThread
  public @NonNull Recipient resolve() {
    Recipient current = recipient.get();

    if (!current.isResolving() || current.getId().isUnknown()) {
      return current;
    }

    if (Util.isMainThread()) {
      Log.w(TAG, "[Resolve][MAIN] " + getId(), new Throwable());
    }

    Recipient       updated      = fetchAndCacheRecipientFromDisk(getId());
    List<Recipient> participants = Stream.of(updated.getParticipants())
                                         .filter(Recipient::isResolving)
                                         .map(Recipient::getId)
                                         .map(this::fetchAndCacheRecipientFromDisk)
                                         .toList();

    for (Recipient participant : participants) {
      participant.live().set(participant);
    }

    set(updated);

    return updated;
  }

  @WorkerThread
  public void refresh() {
    refresh(getId());
  }

  /**
   * Forces a reload of the underlying recipient.
   */
  @WorkerThread
  public void refresh(@NonNull RecipientId id) {
    if (!getId().equals(id)) {
      Log.w(TAG, "Switching ID from " + getId() + " to " + id);
    }

    if (getId().isUnknown()) return;

    if (Util.isMainThread()) {
      Log.w(TAG, "[Refresh][MAIN] " + id, new Throwable());
    }

    Recipient       recipient    = fetchAndCacheRecipientFromDisk(id);
    List<Recipient> participants = Stream.of(recipient.getParticipants())
                                         .map(Recipient::getId)
                                         .map(this::fetchAndCacheRecipientFromDisk)
                                         .toList();

    for (Recipient participant : participants) {
      participant.live().set(participant);
    }

    set(recipient);
    refreshForceNotify.postValue(new Object());
  }

  public @NonNull LiveData<Recipient> getLiveData() {
    return observableLiveData;
  }

  private @NonNull Recipient fetchAndCacheRecipientFromDisk(@NonNull RecipientId id) {
    RecipientSettings settings = recipientDatabase.getRecipientSettings(id);
    RecipientDetails  details  = settings.getGroupId() != null ? getGroupRecipientDetails(settings)
                                                               : RecipientDetails.forIndividual(context, settings);

    Recipient recipient = new Recipient(id, details, true);
    RecipientIdCache.INSTANCE.put(recipient);
    return recipient;
  }

  @WorkerThread
  private @NonNull RecipientDetails getGroupRecipientDetails(@NonNull RecipientSettings settings) {
    Optional<GroupRecord> groupRecord = groupDatabase.getGroup(settings.getId());

    if (groupRecord.isPresent()) {
      String          title    = groupRecord.get().getTitle();
      List<Recipient> members  = Stream.of(groupRecord.get().getMembers()).filterNot(RecipientId::isUnknown).map(this::fetchAndCacheRecipientFromDisk).toList();
      Optional<Long>  avatarId = Optional.absent();

      if (groupRecord.get().hasAvatar()) {
        avatarId = Optional.of(groupRecord.get().getAvatarId());
      }

      return new RecipientDetails(title, avatarId, false, false, settings, members);
    }

    return new RecipientDetails(null, Optional.absent(), false, false, settings, null);
  }

  synchronized void set(@NonNull Recipient recipient) {
    this.recipient.set(recipient);
    this.liveData.postValue(recipient);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LiveRecipient that = (LiveRecipient) o;
    return recipient.equals(that.recipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipient);
  }
}
