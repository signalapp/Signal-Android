package org.thoughtcrime.securesms.util;

import android.Manifest;
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
import com.google.common.collect.Sets;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util.AppIconUtility;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobs.ConversationShortcutUpdateJob;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ConversationUtil encapsulates support for Android 11+'s new Conversations system
 */
public final class ConversationUtil {

  private static final String TAG = Log.tag(ConversationUtil.class);

  public static final int CONVERSATION_SUPPORT_VERSION = 30;

  private static final String CATEGORY_SHARE_TARGET = "org.thoughtcrime.securesms.sharing.CATEGORY_SHARE_TARGET";

  private static final String CAPABILITY_SEND_MESSAGE    = "actions.intent.SEND_MESSAGE";
  private static final String CAPABILITY_RECEIVE_MESSAGE = "actions.intent.RECEIVE_MESSAGE";

  private static final String PARAMETER_RECIPIENT_TYPE = "message.recipient.@type";
  private static final String PARAMETER_SENDER_TYPE    = "message.sender.@type";

  private static final List<String> PARAMETERS_AUDIENCE = Collections.singletonList("Audience");

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
   * Synchronously pushes a dynamic shortcut for the given recipient.
   * <p>
   * The recipient is given a high ranking with the intention of not appearing immediately in results.
   *
   * @return True if it succeeded, or false if it was rate-limited.
   */
  @WorkerThread
  public static boolean pushShortcutForRecipientSync(@NonNull Context context, @NonNull Recipient recipient, @NonNull Direction direction ) {
    List<ShortcutInfoCompat> shortcuts  = ShortcutManagerCompat.getDynamicShortcuts(context);
    return pushShortcutForRecipientInternal(context, recipient, shortcuts.size(), direction);
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
    return Math.min(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context), 10);
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

    ComponentName activityName = new AppIconUtility(context).currentAppIconComponentName();

    for (int i = 0; i < rankedRecipients.size(); i++) {
      ShortcutInfoCompat info = buildShortcutInfo(context, activityName, rankedRecipients.get(i), i, Direction.NONE);
      shortcuts.add(info);
    }

    return ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
  }

  /**
   * Pushes a dynamic shortcut for a given recipient to the shortcut manager
   *
   * @return True if it succeeded, or false if it was rate-limited.
   */
  @WorkerThread
  private static boolean pushShortcutForRecipientInternal(@NonNull Context context, @NonNull Recipient recipient, int rank, @NonNull Direction direction) {

    ComponentName activityName = new AppIconUtility(context).currentAppIconComponentName();

    ShortcutInfoCompat shortcutInfo = buildShortcutInfo(context, activityName, recipient, rank, direction);

    return ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo);
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
                                                               @NonNull ComponentName activity,
                                                               @NonNull Recipient recipient,
                                                               int rank,
                                                               @NonNull Direction direction)
  {
    Recipient resolved   = recipient.resolve();
    Person[]  persons    = buildPersons(context, resolved);
    long      threadId   = SignalDatabase.threads().getOrCreateThreadIdFor(resolved);
    String    shortName  = resolved.isSelf() ? context.getString(R.string.note_to_self) : resolved.getShortDisplayName(context);
    String    longName   = resolved.isSelf() ? context.getString(R.string.note_to_self) : resolved.getDisplayName(context);
    String    shortcutId = getShortcutId(resolved);

    ShortcutInfoCompat.Builder builder = new ShortcutInfoCompat.Builder(context, shortcutId)
                                 .setLongLived(true)
                                 .setIntent(ConversationIntents.createBuilderSync(context, resolved.getId(), threadId).build())
                                 .setShortLabel(shortName)
                                 .setLongLabel(longName)
                                 .setIcon(AvatarUtil.getIconCompat(context, resolved))
                                 .setPersons(persons)
                                 .setCategories(Sets.newHashSet(CATEGORY_SHARE_TARGET))
                                 .setActivity(activity)
                                 .setRank(rank)
                                 .setLocusId(new LocusIdCompat(shortcutId));

    if (direction == Direction.OUTGOING) {
      if (recipient.isGroup()) {
        builder.addCapabilityBinding(CAPABILITY_SEND_MESSAGE, PARAMETER_RECIPIENT_TYPE, PARAMETERS_AUDIENCE);
      } else {
        builder.addCapabilityBinding(CAPABILITY_SEND_MESSAGE);
      }
    } else if (direction == Direction.INCOMING) {
      if (recipient.isGroup()) {
        builder.addCapabilityBinding(CAPABILITY_RECEIVE_MESSAGE, PARAMETER_SENDER_TYPE, PARAMETERS_AUDIENCE);
      } else {
        builder.addCapabilityBinding(CAPABILITY_RECEIVE_MESSAGE);
      }
    }

    return builder.build();
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
  public static @NonNull Person buildPersonWithoutIcon(@NonNull Context context, @NonNull Recipient recipient) {
    return new Person.Builder()
                     .setKey(getShortcutId(recipient.getId()))
                     .setName(recipient.getDisplayName(context))
                     .setUri(recipient.isSystemContact() && Permissions.hasAny(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) ? recipient.getContactUri().toString() : null)
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
                     .setIcon(AvatarUtil.getIconCompat(context, recipient))
                     .setUri(recipient.isSystemContact() && Permissions.hasAny(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) ? recipient.getContactUri().toString() : null)
                     .build();
  }

  public enum Direction {
    NONE(0), INCOMING(1), OUTGOING(2);

    private final int value;

    Direction(int value) {
      this.value = value;
    }

    public int serialize() {
      return value;
    }

    public static Direction deserialize(int value) {
      switch (value) {
        case 0: return NONE;
        case 1: return INCOMING;
        case 2: return OUTGOING;
        default: throw new IllegalArgumentException("Unrecognized value: " + value);
      }
    }
  }
}
