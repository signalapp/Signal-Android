package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class LiveRecipient {

  private final Context                       context;
  private final MutableLiveData<Recipient>    liveData;
  private final Set<RecipientForeverObserver> observers;
  private final Observer<Recipient>           foreverObserver;
  private final Recipient                     defaultRecipient;
  private final RecipientDatabase             recipientDatabase;
  private final GroupDatabase                 groupDatabase;
  private final String                        unnamedGroupName;

  LiveRecipient(@NonNull Context context, @NonNull MutableLiveData<Recipient> liveData, @NonNull Recipient defaultRecipient) {
    this.context           = context.getApplicationContext();
    this.liveData          = liveData;
    this.defaultRecipient  = defaultRecipient;
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    this.unnamedGroupName  = context.getString(R.string.RecipientProvider_unnamed_group);
    this.observers         = new CopyOnWriteArraySet<>();
    this.foreverObserver   = recipient -> {
      for (RecipientForeverObserver o : observers) {
        o.onRecipientChanged(recipient);
      }
    };
  }

  public @NonNull RecipientId getId() {
    return defaultRecipient.getId();
  }

  /**
   * @return A recipient that may or may not be fully-resolved.
   */
  public @NonNull Recipient get() {
    Recipient live = liveData.getValue();

    if (live == null) {
      return defaultRecipient;
    } else {
      return live;
    }
  }

  /**
   * Watch the recipient for changes. The callback will only be invoked if the provided lifecycle is
   * in a valid state. No need to remove the observer. If you do wish to remove the observer (if,
   * for instance, you wish to remove the listener before the end of the owner's lifecycle), you can
   * use {@link #removeObservers(LifecycleOwner)}.
   */
  public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<Recipient> observer) {
    Util.postToMain(() -> liveData.observe(owner, observer));
  }

  /**
   * Removes all observers of this data registered for the given LifecycleOwner.
   */
  public void removeObservers(@NonNull LifecycleOwner owner) {
    Util.runOnMain(() -> liveData.removeObservers(owner));
  }

  /**
   * Watch the recipient for changes. The callback could be invoked at any time. You MUST call
   * {@link #removeForeverObserver(RecipientForeverObserver)} when finished. You should use
   * {@link #observe(LifecycleOwner, Observer<Recipient>)} if possible, as it is lifecycle-safe.
   */
  public void observeForever(@NonNull RecipientForeverObserver observer) {
    Util.postToMain(() -> {
      observers.add(observer);

      if (observers.size() == 1) {
        liveData.observeForever(foreverObserver);
      }
    });
  }

  /**
   * Unsubscribes the provided {@link RecipientForeverObserver} from future changes.
   */
  public void removeForeverObserver(@NonNull RecipientForeverObserver observer) {
    Util.postToMain(() -> {
      observers.remove(observer);

      if (observers.isEmpty()) {
        liveData.removeObserver(foreverObserver);
      }
    });
  }

  /**
   * @return A fully-resolved version of the recipient. May require reading from disk.
   */
  @WorkerThread
  public synchronized @NonNull Recipient resolve() {
    Recipient recipient = get();

    if (recipient.isResolving()) {
      recipient = fetchRecipientFromDisk(defaultRecipient.getId());
      liveData.postValue(recipient);
      Stream.of(recipient.getParticipants()).forEach(Recipient::resolve);
    }

    return recipient;
  }

  /**
   * Forces a reload of the underlying recipient.
   */
  @WorkerThread
  public synchronized void refresh() {
    Recipient recipient = fetchRecipientFromDisk(defaultRecipient.getId());
    liveData.postValue(recipient);
    Stream.of(recipient.getParticipants()).map(Recipient::live).forEach(LiveRecipient::refresh);
  }


  private @NonNull Recipient fetchRecipientFromDisk(RecipientId id) {
    RecipientSettings settings = recipientDatabase.getRecipientSettings(id);
    RecipientDetails  details  = settings.getAddress().isGroup() ? getGroupRecipientDetails(settings)
                                                                 : getIndividualRecipientDetails(settings);

    return new Recipient(id, details);
  }

  private @NonNull RecipientDetails getIndividualRecipientDetails(RecipientSettings settings) {
    boolean systemContact = !TextUtils.isEmpty(settings.getSystemDisplayName());
    boolean isLocalNumber = settings.getAddress().serialize().equals(TextSecurePreferences.getLocalNumber(context));
    return new RecipientDetails(null, Optional.absent(), systemContact, isLocalNumber, settings, null);
  }

  @WorkerThread
  private @NonNull RecipientDetails getGroupRecipientDetails(@NonNull RecipientSettings settings) {
    Optional<GroupRecord> groupRecord = groupDatabase.getGroup(settings.getId());

    if (groupRecord.isPresent()) {
      String          title    = groupRecord.get().getTitle();
      List<Recipient> members  = Stream.of(groupRecord.get().getMembers()).map(Recipient::resolved).toList();
      Optional<Long>  avatarId = Optional.absent();

      if (!settings.getAddress().isMmsGroup() && title == null) {
        title = unnamedGroupName;
      }

      if (groupRecord.get().getAvatar() != null && groupRecord.get().getAvatar().length > 0) {
        avatarId = Optional.of(groupRecord.get().getAvatarId());
      }

      return new RecipientDetails(title, avatarId, false, false, settings, members);
    }

    return new RecipientDetails(unnamedGroupName, Optional.absent(), false, false, settings, null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LiveRecipient that = (LiveRecipient) o;
    return defaultRecipient.equals(that.defaultRecipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultRecipient);
  }
}
