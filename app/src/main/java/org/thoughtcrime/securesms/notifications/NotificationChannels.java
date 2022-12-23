package org.thoughtcrime.securesms.notifications;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.thoughtcrime.securesms.util.ConversationUtil.CONVERSATION_SUPPORT_VERSION;

public class NotificationChannels {

  private static final String TAG = Log.tag(NotificationChannels.class);

  private static final long[] EMPTY_VIBRATION_PATTERN = new long[] { 0 };

  private static class Version {
    static final int MESSAGES_CATEGORY   = 2;
    static final int CALLS_PRIORITY_BUMP = 3;
    static final int VIBRATE_OFF_OTHER   = 4;
  }

  private static final int VERSION = 4;

  private static final String CATEGORY_MESSAGES = "messages";
  private static final String CONTACT_PREFIX    = "contact_";
  private static final String MESSAGES_PREFIX   = "messages_";

  public final String CALLS         = "calls_v3";
  public final String FAILURES      = "failures";
  public final String APP_UPDATES   = "app_updates";
  public final String BACKUPS       = "backups_v2";
  public final String LOCKED_STATUS = "locked_status_v2";
  public final String OTHER         = "other_v3";
  public final String VOICE_NOTES   = "voice_notes";
  public final String JOIN_EVENTS   = "join_events";
  public final String BACKGROUND    = "background_connection";
  public final String CALL_STATUS   = "call_status";
  public final String APP_ALERTS    = "app_alerts";

  private static volatile NotificationChannels instance;

  private final Application context;

  public static NotificationChannels getInstance() {
    if (instance == null) {
      synchronized (NotificationChannels.class) {
        if (instance == null) {
          instance = new NotificationChannels(ApplicationDependencies.getApplication());
        }
      }
    }

    return instance;
  }

  /**
   * Ensures all of the notification channels are created. No harm in repeat calls. Call is safely
   * ignored for API < 26.
   */
  private NotificationChannels(@NonNull Application application) {
    this.context = application;

    if (!supported()) {
      return;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    int oldVersion = TextSecurePreferences.getNotificationChannelVersion(context);
    if (oldVersion != VERSION) {
      onUpgrade(notificationManager, oldVersion, VERSION);
      TextSecurePreferences.setNotificationChannelVersion(context, VERSION);
    }

    onCreate(notificationManager);

    AsyncTask.SERIAL_EXECUTOR.execute(this::ensureCustomChannelConsistency);
  }

  /**
   * @return Whether or not notification channels are supported.
   */
  public static boolean supported() {
    return Build.VERSION.SDK_INT >= 26;
  }

  /**
   * Navigates the user to the system settings for the desired notification channel.
   */
  public void openChannelSettings(@NonNull Activity activityContext, @NonNull String channelId, @Nullable String conversationId) {
    if (!supported()) {
      return;
    }

    try {
      Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
      intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
      if (conversationId != null && Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION) {
        intent.putExtra(Settings.EXTRA_CONVERSATION_ID, conversationId);
      }
      activityContext.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, "Channel settings activity not found", e);
      Toast.makeText(activityContext, R.string.NotificationChannels__no_activity_available_to_open_notification_channel_settings, Toast.LENGTH_SHORT).show();
    }
  }


  /**
   * @return A name suitable to be displayed as the notification channel title.
   */
  public @NonNull String getChannelDisplayNameFor(@Nullable String systemName,
                                                  @Nullable String profileName,
                                                  @Nullable String username,
                                                  @NonNull String address)
  {
    if (!TextUtils.isEmpty(systemName)) {
      return systemName;
    } else if (!TextUtils.isEmpty(profileName)) {
      return profileName;
    } else if (!TextUtils.isEmpty(username)) {
      return username;
    } else if (!TextUtils.isEmpty(address)) {
      return address;
    } else {
      return context.getString(R.string.NotificationChannel_missing_display_name);
    }
  }

  /**
   * Whether or not notifications for the entire app are enabled.
   */
  public synchronized boolean areNotificationsEnabled() {
    if (Build.VERSION.SDK_INT >= 24) {
      return ServiceUtil.getNotificationManager(context).areNotificationsEnabled();
    } else {
      return true;
    }
  }

  /**
   * Recreates all notification channels for contacts with custom notifications enabled. Should be
   * safe to call repeatedly. Needs to be executed on a background thread.
   */
  @WorkerThread
  public synchronized void restoreContactNotificationChannels() {
    if (!NotificationChannels.supported()) {
      return;
    }

    RecipientTable db = SignalDatabase.recipients();

    try (RecipientTable.RecipientReader reader = db.getRecipientsWithNotificationChannels()) {
      Recipient recipient;
      while ((recipient = reader.getNext()) != null) {
        NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
        if (!channelExists(notificationManager.getNotificationChannel(recipient.getNotificationChannel()))) {
          String id = createChannelFor(recipient);
          db.setNotificationChannel(recipient.getId(), id);
        }
      }
    }

    ensureCustomChannelConsistency();
  }

  /**
   * @return The channel ID for the default messages channel.
   */
  public synchronized @NonNull String getMessagesChannel() {
    return getMessagesChannelId(TextSecurePreferences.getNotificationMessagesChannelVersion(context));
  }

  /**
   * Creates a channel for the specified recipient.
   * @return The channel ID for the newly-created channel.
   */
  public synchronized @Nullable String createChannelFor(@NonNull Recipient recipient) {
    if (recipient.getId().isUnknown()) return null;

    VibrateState vibrateState     = recipient.getMessageVibrate();
    boolean      vibrationEnabled = vibrateState == VibrateState.DEFAULT ? SignalStore.settings().isMessageVibrateEnabled() : vibrateState == VibrateState.ENABLED;
    Uri          messageRingtone  = recipient.getMessageRingtone() != null ? recipient.getMessageRingtone() : getMessageRingtone();
    String       displayName      = recipient.getDisplayName(context);

    return createChannelFor(generateChannelIdFor(recipient), displayName, messageRingtone, vibrationEnabled, ConversationUtil.getShortcutId(recipient));
  }

  /**
   * More verbose version of {@link #createChannelFor(Recipient)}.
   */
  public synchronized @Nullable String createChannelFor(@NonNull String channelId,
                                                        @NonNull String displayName,
                                                        @Nullable Uri messageSound,
                                                        boolean vibrationEnabled,
                                                        @Nullable String shortcutId)
  {
    if (!supported()) {
      return null;
    }

    NotificationChannel channel = new NotificationChannel(channelId, displayName, NotificationManager.IMPORTANCE_HIGH);

    setLedPreference(channel, SignalStore.settings().getMessageLedColor());
    channel.setGroup(CATEGORY_MESSAGES);
    setVibrationEnabled(channel, vibrationEnabled);

    if (messageSound != null) {
      channel.setSound(messageSound, new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                                                                  .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                                                                  .build());
    }

    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION && shortcutId != null) {
      channel.setConversationId(getMessagesChannel(), shortcutId);
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    notificationManager.createNotificationChannel(channel);

    return channelId;
  }

  /**
   * Deletes the channel generated for the provided recipient. Safe to call even if there was never
   * a channel made for that recipient.
   */
  public synchronized void deleteChannelFor(@NonNull Recipient recipient) {
    if (!supported()) {
      return;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    String              channel             = recipient.getNotificationChannel();

    if (channel != null) {
      Log.i(TAG, "Deleting channel");
      notificationManager.deleteNotificationChannel(channel);
    }
  }

  /**
   * Updates the LED color for message notifications and all contact-specific message notification
   * channels. Performs database operations and should therefore be invoked on a background thread.
   */
  @WorkerThread
  public synchronized void updateMessagesLedColor(@NonNull String color) {
    if (!supported()) {
      return;
    }
    Log.i(TAG, "Updating LED color.");

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    updateMessageChannel(channel -> setLedPreference(channel, color));
    updateAllRecipientChannelLedColors(notificationManager, color);

    ensureCustomChannelConsistency();
  }

  /**
   * @return The message ringtone set for the default message channel.
   */
  public synchronized @NonNull Uri getMessageRingtone() {
    if (!supported()) {
      return Uri.EMPTY;
    }

    Uri sound = ServiceUtil.getNotificationManager(context).getNotificationChannel(getMessagesChannel()).getSound();
    return sound == null ? Uri.EMPTY : sound;
  }

  public synchronized @Nullable Uri getMessageRingtone(@NonNull Recipient recipient) {
    if (!supported() || recipient.resolve().getNotificationChannel() == null) {
      return null;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannel channel             = notificationManager.getNotificationChannel(recipient.getNotificationChannel());

    if (!channelExists(channel)) {
      Log.w(TAG, "Recipient had no channel. Returning null.");
      return null;
    }

    Uri channelSound = channel.getSound();
    return channelSound != null ? channelSound : Uri.EMPTY;
  }

  /**
   * Update the message ringtone for the default message channel.
   */
  public synchronized void updateMessageRingtone(@Nullable Uri uri) {
    if (!supported()) {
      return;
    }
    Log.i(TAG, "Updating default message ringtone with URI: " + uri);

    updateMessageChannel(channel -> {
      channel.setSound(uri == null ? Settings.System.DEFAULT_NOTIFICATION_URI : uri, getRingtoneAudioAttributes());
    });
  }

  /**
   * Updates the message ringtone for a specific recipient. If that recipient has no channel, this
   * does nothing.
   *
   * This has to update the database, and therefore should be run on a background thread.
   */
  @WorkerThread
  public synchronized void updateMessageRingtone(@NonNull Recipient recipient, @Nullable Uri uri) {
    if (!supported() || recipient.getNotificationChannel() == null) {
      return;
    }
    Log.i(TAG, "Updating recipient message ringtone with URI: " + uri);

    String  newChannelId = generateChannelIdFor(recipient);
    boolean success      = updateExistingChannel(ServiceUtil.getNotificationManager(context),
                                                 recipient.getNotificationChannel(),
                                                 generateChannelIdFor(recipient),
                                                 channel -> channel.setSound(uri == null ? Settings.System.DEFAULT_NOTIFICATION_URI : uri, getRingtoneAudioAttributes()));

    SignalDatabase.recipients().setNotificationChannel(recipient.getId(), success ? newChannelId : null);
    ensureCustomChannelConsistency();
  }

  /**
   * @return The vibrate settings for the default message channel.
   */
  public synchronized boolean getMessageVibrate() {
    if (!supported()) {
      return false;
    }

    return ServiceUtil.getNotificationManager(context).getNotificationChannel(getMessagesChannel()).shouldVibrate();
  }

  /**
   * @return The vibrate setting for a specific recipient. If that recipient has no channel, this
   *         will return the setting for the default message channel.
   */
  public synchronized boolean getMessageVibrate(@NonNull Recipient recipient) {
    if (!supported()) {
      return getMessageVibrate();
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannel channel             = notificationManager.getNotificationChannel(recipient.getNotificationChannel());

    if (!channelExists(channel)) {
      Log.w(TAG, "Recipient didn't have a channel. Returning message default.");
      return getMessageVibrate();
    }

    return channel.shouldVibrate() && !Arrays.equals(channel.getVibrationPattern(), EMPTY_VIBRATION_PATTERN);
  }

  /**
   * Sets the vibrate property for the default message channel.
   */
  public synchronized void updateMessageVibrate(boolean enabled) {
    if (!supported()) {
      return;
    }
    Log.i(TAG, "Updating default vibrate with value: " + enabled);

    updateMessageChannel(channel -> setVibrationEnabled(channel, enabled));
  }

  /**
   * Updates the message ringtone for a specific recipient. If that recipient has no channel, this
   * does nothing.
   *
   * This has to update the database and should therefore be run on a background thread.
   */
  @WorkerThread
  public synchronized void updateMessageVibrate(@NonNull Recipient recipient, VibrateState vibrateState) {
    if (!supported() || recipient.getNotificationChannel() == null) {
      return ;
    }
    Log.i(TAG, "Updating recipient vibrate with value: " + vibrateState);

    boolean enabled      = vibrateState == VibrateState.DEFAULT ? getMessageVibrate() : vibrateState == VibrateState.ENABLED;
    String  newChannelId = generateChannelIdFor(recipient);

    boolean success = updateExistingChannel(ServiceUtil.getNotificationManager(context),
                                            recipient.getNotificationChannel(),
                                            newChannelId,
                                            channel -> setVibrationEnabled(channel, enabled));

    SignalDatabase.recipients().setNotificationChannel(recipient.getId(), success ? newChannelId : null);
    ensureCustomChannelConsistency();
  }

  /**
   * Some devices don't seem to respect the vibration flag on a notification channel. To disable, we
   * instead set the pattern to be empty.
   *
   * Note: Calling {@link NotificationChannel#setVibrationPattern(long[])} with null will clear the empty
   * vibration pattern (if any) but also set the enable vibration flag to false, hence the two steps to enable.
   * Likewise, setting the pattern to any non-zero length array will set enable vibration flag to true.
   */
  @TargetApi(26)
  private void setVibrationEnabled(@NonNull NotificationChannel channel, boolean enabled) {
    if (enabled) {
      channel.setVibrationPattern(null);
      channel.enableVibration(true);
    } else {
      channel.setVibrationPattern(EMPTY_VIBRATION_PATTERN);
    }
  }

  /**
   * Whether or not the default messages notification channel is enabled. Note that "enabled" just
   * means receiving notifications in some capacity -- a user could have it enabled, but set it to a
   * lower importance.
   *
   * This could also return true if the specific channnel is enabled, but notifications *overall*
   * are disabled, or the messages category is disabled. Check
   * {@link #areNotificationsEnabled()} and {@link #isMessagesChannelGroupEnabled()}
   * to be safe.
   */
  public synchronized boolean isMessageChannelEnabled() {
    if (!supported()) {
      return true;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannel channel             = notificationManager.getNotificationChannel(getMessagesChannel());

    return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
  }

  /**
   * Whether or not the notification category for messages is enabled. Note that even if it is,
   * a user could have blocked the specific channel, or notifications overall, and it'd still be
   * true. See {@link #isMessageChannelEnabled()} and {@link #areNotificationsEnabled()}.
   */
  public synchronized boolean isMessagesChannelGroupEnabled() {
    if (Build.VERSION.SDK_INT < 28) {
      return true;
    }

    NotificationManager      notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannelGroup group               = notificationManager.getNotificationChannelGroup(CATEGORY_MESSAGES);

    return group != null && !group.isBlocked();
  }

  public synchronized boolean isCallsChannelValid() {
    if (!supported()) {
      return true;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannel channel             = notificationManager.getNotificationChannel(CALLS);

    return channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_HIGH;
  }

  /**
   * Attempt to update a recipient with shortcut based notification channel if the system made one for us and we don't
   * have a channel set yet.
   *
   * @return true if a shortcut based notification channel was found and then associated with the recipient, false otherwise
   */
  @WorkerThread
  public boolean updateWithShortcutBasedChannel(@NonNull Recipient recipient) {
    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION && TextUtils.isEmpty(recipient.getNotificationChannel())) {
      String shortcutId = ConversationUtil.getShortcutId(recipient);

      Optional<NotificationChannel> channel = ServiceUtil.getNotificationManager(context)
                                                         .getNotificationChannels()
                                                         .stream()
                                                         .filter(c -> Objects.equals(shortcutId, c.getConversationId()))
                                                         .findFirst();

      if (channel.isPresent()) {
        Log.i(TAG, "Conversation channel created outside of app, while running. Update " + recipient.getId() + " to use '" + channel.get().getId() + "'");
        SignalDatabase.recipients().setNotificationChannel(recipient.getId(), channel.get().getId());
        return true;
      }
    }
    return false;
  }

  /**
   * Updates the name of an existing channel to match the recipient's current name. Will have no
   * effect if the recipient doesn't have an existing valid channel.
   */
  public synchronized void updateContactChannelName(@NonNull Recipient recipient) {
    if (!supported() || recipient.getNotificationChannel() == null) {
      return;
    }
    Log.i(TAG, "Updating contact channel name");

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    if (notificationManager.getNotificationChannel(recipient.getNotificationChannel()) == null) {
      Log.w(TAG, "Tried to update the name of a channel, but that channel doesn't exist.");
      return;
    }

    NotificationChannel channel = new NotificationChannel(recipient.getNotificationChannel(),
                                                          recipient.getDisplayName(context),
                                                          NotificationManager.IMPORTANCE_HIGH);
    channel.setGroup(CATEGORY_MESSAGES);
    notificationManager.createNotificationChannel(channel);
  }

  @TargetApi(26)
  @WorkerThread
  public synchronized void ensureCustomChannelConsistency() {
    if (!supported()) {
      return;
    }
    Log.d(TAG, "ensureCustomChannelConsistency()");

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    RecipientTable      db                  = SignalDatabase.recipients();
    List<Recipient>     customRecipients    = new ArrayList<>();
    Set<String>         customChannelIds    = new HashSet<>();

    try (RecipientTable.RecipientReader reader = db.getRecipientsWithNotificationChannels()) {
      Recipient recipient;
      while ((recipient = reader.getNext()) != null) {
        customRecipients.add(recipient);
        customChannelIds.add(recipient.getNotificationChannel());
      }
    }

    Set<String> existingChannelIds  = Stream.of(notificationManager.getNotificationChannels()).map(NotificationChannel::getId).collect(Collectors.toSet());

    for (NotificationChannel existingChannel : notificationManager.getNotificationChannels()) {
      if ((existingChannel.getId().startsWith(CONTACT_PREFIX) || existingChannel.getId().startsWith(MESSAGES_PREFIX)) &&
          Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION &&
          existingChannel.getConversationId() != null)
      {
        if (customChannelIds.contains(existingChannel.getId())) {
          continue;
        }

        RecipientId id = ConversationUtil.getRecipientId(existingChannel.getConversationId());
        if (id != null) {
          Log.i(TAG, "Consistency: Conversation channel created outside of app, update " + id + " to use '" + existingChannel.getId() + "'");
          db.setNotificationChannel(id, existingChannel.getId());
        } else {
          Log.i(TAG, "Consistency: Conversation channel created outside of app with no matching recipient, deleting channel '" + existingChannel.getId() + "'");
          notificationManager.deleteNotificationChannel(existingChannel.getId());
        }
      } else if (existingChannel.getId().startsWith(CONTACT_PREFIX) && !customChannelIds.contains(existingChannel.getId())) {
        Log.i(TAG, "Consistency: Deleting channel '"+ existingChannel.getId() + "' because the DB has no record of it.");
        notificationManager.deleteNotificationChannel(existingChannel.getId());
      } else if (existingChannel.getId().startsWith(MESSAGES_PREFIX) && !existingChannel.getId().equals(getMessagesChannel())) {
        Log.i(TAG, "Consistency: Deleting channel '" + existingChannel.getId() + "' because it's out of date.");
        notificationManager.deleteNotificationChannel(existingChannel.getId());
      }
    }

    for (Recipient customRecipient : customRecipients) {
      if (!existingChannelIds.contains(customRecipient.getNotificationChannel())) {
        Log.i(TAG, "Consistency: Removing custom channel '"+ customRecipient.getNotificationChannel() + "' because the system doesn't have it.");
        db.setNotificationChannel(customRecipient.getId(), null);
      }
    }
  }

  public synchronized void onLocaleChanged() {
    if (!supported()) {
      return;
    }
    onCreate(ServiceUtil.getNotificationManager(context));
  }

  @TargetApi(26)
  private void onCreate(@NonNull NotificationManager notificationManager) {
    NotificationChannelGroup messagesGroup = new NotificationChannelGroup(CATEGORY_MESSAGES, context.getResources().getString(R.string.NotificationChannel_group_chats));
    notificationManager.createNotificationChannelGroup(messagesGroup);

    NotificationChannel messages     = new NotificationChannel(getMessagesChannel(), context.getString(R.string.NotificationChannel_channel_messages), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel calls        = new NotificationChannel(CALLS, context.getString(R.string.NotificationChannel_calls), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel failures     = new NotificationChannel(FAILURES, context.getString(R.string.NotificationChannel_failures), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel backups      = new NotificationChannel(BACKUPS, context.getString(R.string.NotificationChannel_backups), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel lockedStatus = new NotificationChannel(LOCKED_STATUS, context.getString(R.string.NotificationChannel_locked_status), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel other        = new NotificationChannel(OTHER, context.getString(R.string.NotificationChannel_other), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel voiceNotes   = new NotificationChannel(VOICE_NOTES, context.getString(R.string.NotificationChannel_voice_notes), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel joinEvents   = new NotificationChannel(JOIN_EVENTS, context.getString(R.string.NotificationChannel_contact_joined_signal), NotificationManager.IMPORTANCE_DEFAULT);
    NotificationChannel background   = new NotificationChannel(BACKGROUND, context.getString(R.string.NotificationChannel_background_connection), getDefaultBackgroundChannelImportance(notificationManager));
    NotificationChannel callStatus   = new NotificationChannel(CALL_STATUS, context.getString(R.string.NotificationChannel_call_status), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel appAlerts    = new NotificationChannel(APP_ALERTS, context.getString(R.string.NotificationChannel_critical_app_alerts), NotificationManager.IMPORTANCE_HIGH);

    messages.setGroup(CATEGORY_MESSAGES);
    setVibrationEnabled(messages, SignalStore.settings().isMessageVibrateEnabled());
    messages.setSound(SignalStore.settings().getMessageNotificationSound(), getRingtoneAudioAttributes());
    setLedPreference(messages, SignalStore.settings().getMessageLedColor());

    calls.setShowBadge(false);
    backups.setShowBadge(false);
    lockedStatus.setShowBadge(false);
    other.setShowBadge(false);
    setVibrationEnabled(other, false);
    voiceNotes.setShowBadge(false);
    joinEvents.setShowBadge(false);
    background.setShowBadge(false);
    callStatus.setShowBadge(false);
    appAlerts.setShowBadge(false);

    notificationManager.createNotificationChannels(Arrays.asList(messages, calls, failures, backups, lockedStatus, other, voiceNotes, joinEvents, background, callStatus, appAlerts));

    if (BuildConfig.PLAY_STORE_DISABLED) {
      NotificationChannel appUpdates = new NotificationChannel(APP_UPDATES, context.getString(R.string.NotificationChannel_app_updates), NotificationManager.IMPORTANCE_HIGH);
      notificationManager.createNotificationChannel(appUpdates);
    } else {
      notificationManager.deleteNotificationChannel(APP_UPDATES);
    }
  }

  @TargetApi(26)
  private int getDefaultBackgroundChannelImportance(NotificationManager notificationManager) {
    NotificationChannel existingOther = notificationManager.getNotificationChannel(OTHER);

    if (existingOther != null && existingOther.getImportance() != NotificationManager.IMPORTANCE_LOW) {
      return existingOther.getImportance();
    } else {
      return NotificationManager.IMPORTANCE_LOW;
    }
  }


  @TargetApi(26)
  private static void onUpgrade(@NonNull NotificationManager notificationManager, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading channels from " + oldVersion + " to " + newVersion);

    if (oldVersion < Version.MESSAGES_CATEGORY) {
      notificationManager.deleteNotificationChannel("messages");
      notificationManager.deleteNotificationChannel("calls");
      notificationManager.deleteNotificationChannel("locked_status");
      notificationManager.deleteNotificationChannel("backups");
      notificationManager.deleteNotificationChannel("other");
    }

    if (oldVersion < Version.CALLS_PRIORITY_BUMP) {
      notificationManager.deleteNotificationChannel("calls_v2");
    }

    if (oldVersion < Version.VIBRATE_OFF_OTHER) {
      notificationManager.deleteNotificationChannel("other_v2");
    }
  }

  @TargetApi(26)
  private static void setLedPreference(@NonNull NotificationChannel channel, @NonNull String ledColor) {
    if ("none".equals(ledColor)) {
      channel.enableLights(false);
    } else {
      channel.enableLights(true);
      channel.setLightColor(Color.parseColor(ledColor));
    }
  }


  private static @NonNull String generateChannelIdFor(@NonNull Recipient recipient) {
    return CONTACT_PREFIX + recipient.getId().serialize() + "_" + System.currentTimeMillis();
  }

  @TargetApi(26)
  private static @NonNull NotificationChannel copyChannel(@NonNull NotificationChannel original, @NonNull String id) {
    NotificationChannel copy = new NotificationChannel(id, original.getName(), original.getImportance());

    copy.setGroup(original.getGroup());
    copy.setSound(original.getSound(), original.getAudioAttributes());
    copy.setBypassDnd(original.canBypassDnd());
    copy.setVibrationPattern(original.getVibrationPattern());
    copy.enableVibration(original.shouldVibrate());
    copy.setLockscreenVisibility(original.getLockscreenVisibility());
    copy.setShowBadge(original.canShowBadge());
    copy.setLightColor(original.getLightColor());
    copy.enableLights(original.shouldShowLights());

    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION && original.getConversationId() != null) {
      copy.setConversationId(original.getParentChannelId(), original.getConversationId());
    }

    return copy;
  }

  private static String getMessagesChannelId(int version) {
    return MESSAGES_PREFIX + version;
  }

  @WorkerThread
  @TargetApi(26)
  private void updateAllRecipientChannelLedColors(@NonNull NotificationManager notificationManager, @NonNull String color) {
    RecipientTable database = SignalDatabase.recipients();

    try (RecipientTable.RecipientReader recipients = database.getRecipientsWithNotificationChannels()) {
      Recipient recipient;
      while ((recipient = recipients.getNext()) != null) {
        assert recipient.getNotificationChannel() != null;

        String  newChannelId = generateChannelIdFor(recipient);
        boolean success      = updateExistingChannel(notificationManager, recipient.getNotificationChannel(), newChannelId, channel -> setLedPreference(channel, color));

        database.setNotificationChannel(recipient.getId(), success ? newChannelId : null);
      }
    }

    ensureCustomChannelConsistency();
  }

  @TargetApi(26)
  private void updateMessageChannel(@NonNull ChannelUpdater updater) {
    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    int existingVersion                     = TextSecurePreferences.getNotificationMessagesChannelVersion(context);
    int newVersion                          = existingVersion + 1;

    Log.i(TAG, "Updating message channel from version " + existingVersion + " to " + newVersion);
    if (updateExistingChannel(notificationManager, getMessagesChannelId(existingVersion), getMessagesChannelId(newVersion), updater)) {
      TextSecurePreferences.setNotificationMessagesChannelVersion(context, newVersion);
    } else {
      onCreate(notificationManager);
    }
  }

  @TargetApi(26)
  private static boolean updateExistingChannel(@NonNull NotificationManager notificationManager,
                                               @NonNull String channelId,
                                               @NonNull String newChannelId,
                                               @NonNull ChannelUpdater updater)
  {
    NotificationChannel existingChannel = notificationManager.getNotificationChannel(channelId);
    if (existingChannel == null) {
      Log.w(TAG, "Tried to update a channel, but it didn't exist.");
      return false;
    }

    notificationManager.deleteNotificationChannel(existingChannel.getId());

    NotificationChannel newChannel = copyChannel(existingChannel, newChannelId);
    updater.update(newChannel);
    notificationManager.createNotificationChannel(newChannel);
    return true;
  }

  @TargetApi(21)
  private static AudioAttributes getRingtoneAudioAttributes() {
    return new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
        .build();
  }

  @TargetApi(26)
  private static boolean channelExists(@Nullable NotificationChannel channel) {
    return channel != null && !NotificationChannel.DEFAULT_CHANNEL_ID.equals(channel.getId());
  }

  private interface ChannelUpdater {
    @TargetApi(26)
    void update(@NonNull NotificationChannel channel);
  }
}
