package org.thoughtcrime.securesms.util;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.Person;
import androidx.core.content.LocusIdCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobs.ConversationShortcutUpdateJob;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ConversationUtil encapsulates support for Android 11+'s new Conversations system
 */
public final class ConversationUtil {

  public static final int CONVERSATION_SUPPORT_VERSION = 30;

  private static final String TAG = Log.tag(ConversationUtil.class);

  private static final String CATEGORY_SHARE_TARGET = "org.thoughtcrime.securesms.sharing.CATEGORY_SHARE_TARGET";

  private ConversationUtil() {}


  /**
   * @return The stringified channel id for a given Recipient
   */
  @WorkerThread
  public static @NonNull String getChannelId(@NonNull Context context, @NonNull Recipient recipient) {
    Recipient resolved = recipient.resolve();

    return resolved.getNotificationChannel() != null ? resolved.getNotificationChannel() : NotificationChannels.getInstance().getMessagesChannel();
  }

  /**
   * Enqueues a job to update the list of shortcuts.
   */
  public static void refreshRecipientShortcuts() {
    ConversationShortcutUpdateJob.enqueue();
  }

  /**
   * Synchronously pushes a new dynamic shortcut for the given recipient if one does not already exist.
   * <p>
   * If added, this recipient is given a high ranking with the intention of not appearing immediately in results.
   */
  @WorkerThread
  public static void pushShortcutForRecipientIfNeededSync(@NonNull Context context, @NonNull Recipient recipient) {
    String                   shortcutId = getShortcutId(recipient);
    List<ShortcutInfoCompat> shortcuts  = ShortcutManagerCompat.getDynamicShortcuts(context);

    boolean hasPushedRecipientShortcut = Stream.of(shortcuts)
                                               .filter(info -> Objects.equals(shortcutId, info.getId()))
                                               .findFirst()
                                               .isPresent();

    if (!hasPushedRecipientShortcut) {
      pushShortcutForRecipientInternal(context, recipient, shortcuts.size());
    }
  }

  /**
   * Clears all currently set dynamic shortcuts
   */
  public static void clearAllShortcuts(@NonNull Context context) {
    List<ShortcutInfoCompat> shortcutInfos = ShortcutManagerCompat.getDynamicShortcuts(context);

    ShortcutManagerCompat.removeLongLivedShortcuts(context, Stream.of(shortcutInfos).map(ShortcutInfoCompat::getId).toList());
  }

  /**
   * Clears the shortcuts tied to a given thread.
   */
  public static void clearShortcuts(@NonNull Context context, @NonNull Collection<RecipientId> recipientIds) {
    SignalExecutors.BOUNDED.execute(() -> {
      ShortcutManagerCompat.removeLongLivedShortcuts(context, Stream.of(recipientIds).withoutNulls().map(ConversationUtil::getShortcutId).toList());
    });
  }

  /**
   * Returns an ID that is unique between all recipients.
   *
   * @param recipientId The recipient ID to get a shortcut ID for
   * @return A unique identifier that is stable for a given recipient id
   */
  public static @NonNull String getShortcutId(@NonNull RecipientId recipientId) {
    return recipientId.serialize();
  }

  /**
   * Returns an ID that is unique between all recipients.
   *
   * @param recipient The recipient to get a shortcut for.
   * @return A unique identifier that is stable for a given recipient id
   */
  public static @NonNull String getShortcutId(@NonNull Recipient recipient) {
    return getShortcutId(recipient.getId());
  }

  /**
   * Extract the recipient id from the provided shortcutId.
   */
  public static @Nullable RecipientId getRecipientId(@Nullable String shortcutId) {
    if (shortcutId == null) {
      return null;
    }

    try {
      return RecipientId.from(shortcutId);
    } catch (Throwable t) {
      Log.d(TAG, "Unable to parse recipientId from shortcutId", t);
      return null;
    }
  }

  public static int getMaxShortcuts(@NonNull Context context) {
    return Math.min(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context), 150);
  }

  /**
   * Removes the long-lived shortcuts for the given set of recipients.
   */
  @WorkerThread
  public static void removeLongLivedShortcuts(@NonNull Context context, @NonNull Collection<RecipientId> recipients) {
    ShortcutManagerCompat.removeLongLivedShortcuts(context, recipients.stream().map(ConversationUtil::getShortcutId).collect(Collectors.toList()));
  }

  /**
   * Sets the shortcuts to match the provided recipient list. This call may fail due to getting
   * rate-limited.
   *
   * @param rankedRecipients The recipients in descending priority order. Meaning the most important
   *                         recipient should be first in the list.
   * @return True if the update was successful, false if we were rate-limited.
   */
  @WorkerThread
  public static boolean setActiveShortcuts(@NonNull Context context, @NonNull List<Recipient> rankedRecipients) {
    if (ShortcutManagerCompat.isRateLimitingActive(context)) {
      return false;
    }

    int maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context);

    if (rankedRecipients.size() > maxShortcuts) {
      Log.w(TAG, "Too many recipients provided! Provided: " + rankedRecipients.size() + ", Max: " + maxShortcuts);
      rankedRecipients = rankedRecipients.subList(0, maxShortcuts);
    }

    List<ShortcutInfoCompat> shortcuts = new ArrayList<>(rankedRecipients.size());

    for (int i = 0; i < rankedRecipients.size(); i++) {
      ShortcutInfoCompat info = buildShortcutInfo(context, rankedRecipients.get(i), i);
      shortcuts.add(info);
    }

    return ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
  }

  /**
   * Pushes a dynamic shortcut for a given recipient to the shortcut manager
   */
  @WorkerThread
  private static void pushShortcutForRecipientInternal(@NonNull Context context, @NonNull Recipient recipient, int rank) {
    ShortcutInfoCompat shortcutInfo = buildShortcutInfo(context, recipient, rank);

    ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo);
  }

  /**
   * Builds the shortcut info object for a given Recipient.
   *
   * @param context   The Context under which we are operating
   * @param recipient The Recipient to generate a ShortcutInfo for
   * @param rank      The rank that should be assigned to this recipient
   * @return The new ShortcutInfo
   */
  @WorkerThread
  private static @NonNull ShortcutInfoCompat buildShortcutInfo(@NonNull Context context,
                                                               @NonNull Recipient recipient,
                                                               int rank)
  {
    Recipient resolved   = recipient.resolve();
    Person[]  persons    = buildPersons(context, resolved);
    Long      threadId   = SignalDatabase.threads().getThreadIdFor(resolved.getId());
    String    shortName  = resolved.isSelf() ? context.getString(R.string.note_to_self) : resolved.getShortDisplayName(context);
    String    longName   = resolved.isSelf() ? context.getString(R.string.note_to_self) : resolved.getDisplayName(context);
    String    shortcutId = getShortcutId(resolved);
    
    return new ShortcutInfoCompat.Builder(context, shortcutId)
                                 .setLongLived(true)
                                 .setIntent(ConversationIntents.createBuilder(context, resolved.getId(), threadId != null ? threadId : -1).build())
                                 .setShortLabel(shortName)
                                 .setLongLabel(longName)
                                 .setIcon(AvatarUtil.getIconCompatForShortcut(context, resolved))
                                 .setPersons(persons)
                                 .setCategories(Collections.singleton(CATEGORY_SHARE_TARGET))
                                 .setActivity(new ComponentName(context, "org.thoughtcrime.securesms.RoutingActivity"))
                                 .setRank(rank)
                                 .setLocusId(new LocusIdCompat(shortcutId))
                                 .build();
  }

  /**
   * @return an array of Person objects correlating to members of a conversation (other than self)
   */
  @WorkerThread
  private static @NonNull Person[] buildPersons(@NonNull Context context, @NonNull Recipient recipient) {
    if (recipient.isGroup()) {
      return buildPersonsForGroup(context, recipient.getGroupId().get());
    } else {
      return new Person[] { buildPerson(context, recipient) };
    }
  }

  /**
   * @return an array of Person objects correlating to members of a group (other than self)
   */
  @WorkerThread
  private static @NonNull Person[] buildPersonsForGroup(@NonNull Context context, @NonNull GroupId groupId) {
    List<Recipient> members = SignalDatabase.groups().getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

    return Stream.of(members).map(member -> buildPersonWithoutIcon(context, member.resolve())).toArray(Person[]::new);
  }

  /**
   * @return A Person object representing the given Recipient
   */
  @WorkerThread
  public static @NonNull Person buildPersonWithoutIcon(@NonNull Context context, @NonNull Recipient recipient) {
    return new Person.Builder()
                     .setKey(getShortcutId(recipient.getId()))
                     .setName(recipient.getDisplayName(context))
                     .setUri(recipient.isSystemContact() ? recipient.getContactUri().toString() : null)
                     .build();
  }

  /**
   * @return A Compat Library Person object representing the given Recipient
   */
  @WorkerThread
  public static @NonNull Person buildPerson(@NonNull Context context, @NonNull Recipient recipient) {
    return new Person.Builder()
                     .setKey(getShortcutId(recipient.getId()))
                     .setName(recipient.getDisplayName(context))
                     .setIcon(AvatarUtil.getIconForNotification(context, recipient))
                     .setUri(recipient.isSystemContact() ? recipient.getContactUri().toString() : null)
                     .build();
  }
}
