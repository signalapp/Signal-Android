package org.thoughtcrime.securesms.notifications;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.text.TextUtils;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.logging.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationChannels {

  private static final String TAG = NotificationChannels.class.getSimpleName();

  private static final int VERSION_MESSAGES_CATEGORY   = 2;
  private static final int VERSION_CALLS_PRIORITY_BUMP = 3;

  private static final int VERSION = 3;

  private static final String CATEGORY_MESSAGES = "messages";
  private static final String CONTACT_PREFIX    = "contact_";
  private static final String MESSAGES_PREFIX   = "messages_";

  public static final String CALLS         = "calls_v3";
  public static final String FAILURES      = "failures";
  public static final String APP_UPDATES   = "app_updates";
  public static final String BACKUPS       = "backups_v2";
  public static final String LOCKED_STATUS = "locked_status_v2";
  public static final String OTHER         = "other_v2";

  /**
   * Ensures all of the notification channels are created. No harm in repeat calls. Call is safely
   * ignored for API < 26.
   */
  public static synchronized void create(@NonNull Context context) {
    if (!supported()) {
      return;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    int oldVersion = TextSecurePreferences.getNotificationChannelVersion(context);
    if (oldVersion != VERSION) {
      onUpgrade(notificationManager, oldVersion, VERSION);
      TextSecurePreferences.setNotificationChannelVersion(context, VERSION);
    }

    onCreate(context, notificationManager);

    AsyncTask.SERIAL_EXECUTOR.execute(() -> {
      ensureCustomChannelConsistency(context);
    });
  }

  /**
   * Recreates all notification channels for contacts with custom notifications enabled. Should be
   * safe to call repeatedly. Needs to be executed on a background thread.
   */
  @WorkerThread
  public static synchronized void restoreContactNotificationChannels(@NonNull Context context) {
    if (!NotificationChannels.supported()) {
      return;
    }

    RecipientDatabase db = DatabaseFactory.getRecipientDatabase(context);

    try (RecipientDatabase.RecipientReader reader = db.getRecipientsWithNotificationChannels()) {
      Recipient recipient;
      while ((recipient = reader.getNext()) != null) {
        NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
        if (!channelExists(notificationManager.getNotificationChannel(recipient.getNotificationChannel()))) {
          String id = createChannelFor(context, recipient);
          db.setNotificationChannel(recipient, id);
        }
      }
    }

    ensureCustomChannelConsistency(context);
  }

  /**
   * @return The channel ID for the default messages channel.
   */
  public static synchronized @NonNull String getMessagesChannel(@NonNull Context context) {
    return getMessagesChannelId(TextSecurePreferences.getNotificationMessagesChannelVersion(context));
  }

  /**
   * @return Whether or not notification channels are supported.
   */
  public static boolean supported() {
    return Build.VERSION.SDK_INT >= 26;
  }

  /**
   * @return A name suitable to be displayed as the notification channel title.
   */
  public static @NonNull String getChannelDisplayNameFor(@NonNull Context context, @Nullable String systemName, @Nullable String profileName, @NonNull Address address) {
    if (!TextUtils.isEmpty(systemName)) {
      return systemName;
    } else if (!TextUtils.isEmpty(profileName)) {
      return profileName;
    } else if (!TextUtils.isEmpty(address.serialize())) {
      return address.serialize();
    } else {
      return context.getString(R.string.NotificationChannel_missing_display_name);
    }
  }

  /**
   * Creates a channel for the specified recipient.
   * @return The channel ID for the newly-created channel.
   */
  public static synchronized String createChannelFor(@NonNull Context context, @NonNull Recipient recipient) {
    VibrateState vibrateState     = recipient.getMessageVibrate();
    boolean      vibrationEnabled = vibrateState == VibrateState.DEFAULT ? TextSecurePreferences.isNotificationVibrateEnabled(context) : vibrateState == VibrateState.ENABLED;
    Uri          messageRingtone  = recipient.getMessageRingtone() != null ? recipient.getMessageRingtone() : getMessageRingtone(context);
    String       displayName      = getChannelDisplayNameFor(context, recipient.getName(), recipient.getProfileName(), recipient.getAddress());

    return createChannelFor(context, recipient.getAddress(), displayName, messageRingtone, vibrationEnabled);
  }

  /**
   * More verbose version of {@link #createChannelFor(Context, Recipient)}.
   */
  public static synchronized  @Nullable String createChannelFor(@NonNull Context context,
                                                                @NonNull Address address,
                                                                @NonNull String displayName,
                                                                @Nullable Uri messageSound,
                                                                boolean vibrationEnabled)
  {
    if (!supported()) {
      return null;
    }

    String              channelId = generateChannelIdFor(address);
    NotificationChannel channel   = new NotificationChannel(channelId, displayName, NotificationManager.IMPORTANCE_HIGH);

    setLedPreference(channel, TextSecurePreferences.getNotificationLedColor(context));
    channel.setGroup(CATEGORY_MESSAGES);
    channel.enableVibration(vibrationEnabled);

    if (messageSound != null) {
      channel.setSound(messageSound, new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                                                                  .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                                                                  .build());
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    notificationManager.createNotificationChannel(channel);

    return channelId;
  }

  /**
   * Deletes the channel generated for the provided recipient. Safe to call even if there was never
   * a channel made for that recipient.
   */
  public static synchronized void deleteChannelFor(@NonNull Context context, @NonNull Recipient recipient) {
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
   * Navigates the user to the system settings for the desired notification channel.
   */
  public static void openChannelSettings(@NonNull Context context, @NonNull String channelId) {
    if (!supported()) {
      return;
    }

    Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
    intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    context.startActivity(intent);
  }

  /**
   * Updates the LED color for message notifications and all contact-specific message notification
   * channels. Performs database operations and should therefore be invoked on a background thread.
   */
  @WorkerThread
  public static synchronized void updateMessagesLedColor(@NonNull Context context, @NonNull String color) {
    if (!supported()) {
      return;
    }
    Log.i(TAG, "Updating LED color.");

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    updateMessageChannel(context, channel -> setLedPreference(channel, color));
    updateAllRecipientChannelLedColors(context, notificationManager, color);

    ensureCustomChannelConsistency(context);
  }

  /**
   * @return The message ringtone set for the default message channel.
   */
  public static synchronized @NonNull Uri getMessageRingtone(@NonNull Context context) {
    if (!supported()) {
      return Uri.EMPTY;
    }

    Uri sound = ServiceUtil.getNotificationManager(context).getNotificationChannel(getMessagesChannel(context)).getSound();
    return sound == null ? Uri.EMPTY : sound;
  }

  public static synchronized @Nullable Uri getMessageRingtone(@NonNull Context context, @NonNull Recipient recipient) {
    if (!supported() || recipient.getNotificationChannel() == null) {
      return null;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannel channel             = notificationManager.getNotificationChannel(recipient.getNotificationChannel());

    if (!channelExists(channel)) {
      Log.w(TAG, "Recipient had no channel. Returning null.");
      return null;
    }

    return channel.getSound();
  }

  /**
   * Update the message ringtone for the default message channel.
   */
  public static synchronized void updateMessageRingtone(@NonNull Context context, @Nullable Uri uri) {
    if (!supported()) {
      return;
    }
    Log.i(TAG, "Updating default message ringtone with URI: " + String.valueOf(uri));

    updateMessageChannel(context, channel -> {
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
  public static synchronized void updateMessageRingtone(@NonNull Context context, @NonNull Recipient recipient, @Nullable Uri uri) {
    if (!supported() || recipient.getNotificationChannel() == null) {
      return;
    }
    Log.i(TAG, "Updating recipient message ringtone with URI: " + String.valueOf(uri));

    String  newChannelId = generateChannelIdFor(recipient.getAddress());
    boolean success      = updateExistingChannel(ServiceUtil.getNotificationManager(context),
                                                 recipient.getNotificationChannel(),
                                                 generateChannelIdFor(recipient.getAddress()),
                                                 channel -> channel.setSound(uri == null ? Settings.System.DEFAULT_NOTIFICATION_URI : uri, getRingtoneAudioAttributes()));

    DatabaseFactory.getRecipientDatabase(context).setNotificationChannel(recipient, success ? newChannelId : null);
    ensureCustomChannelConsistency(context);
  }

  /**
   * @return The vibrate settings for the default message channel.
   */
  public static synchronized boolean getMessageVibrate(@NonNull Context context) {
    if (!supported()) {
      return false;
    }

    return ServiceUtil.getNotificationManager(context).getNotificationChannel(getMessagesChannel(context)).shouldVibrate();
  }

  /**
   * @return The vibrate setting for a specific recipient. If that recipient has no channel, this
   *         will return the setting for the default message channel.
   */
  public static synchronized boolean getMessageVibrate(@NonNull Context context, @NonNull Recipient recipient) {
    if (!supported()) {
      return getMessageVibrate(context);
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannel channel             = notificationManager.getNotificationChannel(recipient.getNotificationChannel());

    if (!channelExists(channel)) {
      Log.w(TAG, "Recipient didn't have a channel. Returning message default.");
      return getMessageVibrate(context);
    }

    return channel.shouldVibrate();
  }

  /**
   * Sets the vibrate property for the default message channel.
   */
  public static synchronized void updateMessageVibrate(@NonNull Context context, boolean enabled) {
    if (!supported()) {
      return;
    }
    Log.i(TAG, "Updating default vibrate with value: " + enabled);

    updateMessageChannel(context, channel -> channel.enableVibration(enabled));
  }

  /**
   * Updates the message ringtone for a specific recipient. If that recipient has no channel, this
   * does nothing.
   *
   * This has to update the database and should therefore be run on a background thread.
   */
  @WorkerThread
  public static synchronized void updateMessageVibrate(@NonNull Context context, @NonNull Recipient recipient, VibrateState vibrateState) {
    if (!supported() || recipient.getNotificationChannel() == null) {
      return ;
    }
    Log.i(TAG, "Updating recipient vibrate with value: " + vibrateState);

    boolean enabled      = vibrateState == VibrateState.DEFAULT ? getMessageVibrate(context) : vibrateState == VibrateState.ENABLED;
    String  newChannelId = generateChannelIdFor(recipient.getAddress());
    boolean success      = updateExistingChannel(ServiceUtil.getNotificationManager(context),
                                                 recipient.getNotificationChannel(),
                                                 newChannelId,
                                                 channel -> channel.enableVibration(enabled));

    DatabaseFactory.getRecipientDatabase(context).setNotificationChannel(recipient, success ? newChannelId : null);
    ensureCustomChannelConsistency(context);
  }

  /**
   * Updates the name of an existing channel to match the recipient's current name. Will have no
   * effect if the recipient doesn't have an existing valid channel.
   */
  public static synchronized void updateContactChannelName(@NonNull Context context, @NonNull Recipient recipient) {
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
                                                          getChannelDisplayNameFor(context, recipient.getName(), recipient.getProfileName(), recipient.getAddress()),
                                                          NotificationManager.IMPORTANCE_HIGH);
    channel.setGroup(CATEGORY_MESSAGES);
    notificationManager.createNotificationChannel(channel);
  }

  @TargetApi(26)
  @WorkerThread
  public static synchronized void ensureCustomChannelConsistency(@NonNull Context context) {
    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    RecipientDatabase   db                  = DatabaseFactory.getRecipientDatabase(context);
    List<Recipient>     customRecipients    = new ArrayList<>();
    Set<String>         customChannelIds    = new HashSet<>();
    Set<String>         existingChannelIds  = Stream.of(notificationManager.getNotificationChannels()).map(NotificationChannel::getId).collect(Collectors.toSet());

    try (RecipientDatabase.RecipientReader reader = db.getRecipientsWithNotificationChannels()) {
      Recipient recipient;
      while ((recipient = reader.getNext()) != null) {
        customRecipients.add(recipient);
        customChannelIds.add(recipient.getNotificationChannel());
      }
    }

    for (NotificationChannel existingChannel : notificationManager.getNotificationChannels()) {
      if (existingChannel.getId().startsWith(CONTACT_PREFIX) && !customChannelIds.contains(existingChannel.getId())) {
        notificationManager.deleteNotificationChannel(existingChannel.getId());
      } else if (existingChannel.getId().startsWith(MESSAGES_PREFIX) && !existingChannel.getId().equals(getMessagesChannel(context))) {
        notificationManager.deleteNotificationChannel(existingChannel.getId());
      }
    }

    for (Recipient customRecipient : customRecipients) {
      if (!existingChannelIds.contains(customRecipient.getNotificationChannel())) {
        db.setNotificationChannel(customRecipient, null);
      }
    }
  }

  @TargetApi(26)
  private static void onCreate(@NonNull Context context, @NonNull NotificationManager notificationManager) {
    NotificationChannelGroup messagesGroup = new NotificationChannelGroup(CATEGORY_MESSAGES, context.getResources().getString(R.string.NotificationChannel_group_messages));
    notificationManager.createNotificationChannelGroup(messagesGroup);

    NotificationChannel messages     = new NotificationChannel(getMessagesChannel(context), context.getString(R.string.NotificationChannel_messages), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel calls        = new NotificationChannel(CALLS, context.getString(R.string.NotificationChannel_calls), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel failures     = new NotificationChannel(FAILURES, context.getString(R.string.NotificationChannel_failures), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel backups      = new NotificationChannel(BACKUPS, context.getString(R.string.NotificationChannel_backups), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel lockedStatus = new NotificationChannel(LOCKED_STATUS, context.getString(R.string.NotificationChannel_locked_status), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel other        = new NotificationChannel(OTHER, context.getString(R.string.NotificationChannel_other), NotificationManager.IMPORTANCE_LOW);

    messages.setGroup(CATEGORY_MESSAGES);
    messages.enableVibration(TextSecurePreferences.isNotificationVibrateEnabled(context));
    messages.setSound(TextSecurePreferences.getNotificationRingtone(context), getRingtoneAudioAttributes());
    setLedPreference(messages, TextSecurePreferences.getNotificationLedColor(context));

    calls.setShowBadge(false);
    backups.setShowBadge(false);
    lockedStatus.setShowBadge(false);
    other.setShowBadge(false);

    notificationManager.createNotificationChannels(Arrays.asList(messages, calls, failures, backups, lockedStatus, other));

    if (BuildConfig.PLAY_STORE_DISABLED) {
      NotificationChannel appUpdates = new NotificationChannel(APP_UPDATES, context.getString(R.string.NotificationChannel_app_updates), NotificationManager.IMPORTANCE_HIGH);
      notificationManager.createNotificationChannel(appUpdates);
    } else {
      notificationManager.deleteNotificationChannel(APP_UPDATES);
    }
  }

  @TargetApi(26)
  private static void onUpgrade(@NonNull NotificationManager notificationManager, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading channels from " + oldVersion + " to " + newVersion);

    if (oldVersion < VERSION_MESSAGES_CATEGORY) {
      notificationManager.deleteNotificationChannel("messages");
      notificationManager.deleteNotificationChannel("calls");
      notificationManager.deleteNotificationChannel("locked_status");
      notificationManager.deleteNotificationChannel("backups");
      notificationManager.deleteNotificationChannel("other");
    }

    if (oldVersion < VERSION_CALLS_PRIORITY_BUMP) {
      notificationManager.deleteNotificationChannel("calls_v2");
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


  private static @NonNull String generateChannelIdFor(@NonNull Address address) {
    return CONTACT_PREFIX + address.serialize() + "_" + System.currentTimeMillis();
  }

  @TargetApi(26)
  private static @NonNull NotificationChannel copyChannel(@NonNull NotificationChannel original, @NonNull String id) {
    NotificationChannel copy = new NotificationChannel(id, original.getName(), original.getImportance());

    copy.setGroup(original.getGroup());
    copy.setSound(original.getSound(), original.getAudioAttributes());
    copy.setBypassDnd(original.canBypassDnd());
    copy.enableVibration(original.shouldVibrate());
    copy.setVibrationPattern(original.getVibrationPattern());
    copy.setLockscreenVisibility(original.getLockscreenVisibility());
    copy.setShowBadge(original.canShowBadge());
    copy.setLightColor(original.getLightColor());
    copy.enableLights(original.shouldShowLights());

    return copy;
  }

  private static String getMessagesChannelId(int version) {
    return MESSAGES_PREFIX + version;
  }

  @WorkerThread
  @TargetApi(26)
  private static void updateAllRecipientChannelLedColors(@NonNull Context context, @NonNull NotificationManager notificationManager, @NonNull String color) {
    RecipientDatabase database = DatabaseFactory.getRecipientDatabase(context);

    try (RecipientDatabase.RecipientReader recipients = database.getRecipientsWithNotificationChannels()) {
      Recipient recipient;
      while ((recipient = recipients.getNext()) != null) {
        assert recipient.getNotificationChannel() != null;

        String  newChannelId = generateChannelIdFor(recipient.getAddress());
        boolean success      = updateExistingChannel(notificationManager, recipient.getNotificationChannel(), newChannelId, channel -> setLedPreference(channel, color));

        database.setNotificationChannel(recipient, success ? newChannelId : null);
      }
    }

    ensureCustomChannelConsistency(context);
  }

  @TargetApi(26)
  private static void updateMessageChannel(@NonNull Context context, @NonNull ChannelUpdater updater) {
    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    int existingVersion                     = TextSecurePreferences.getNotificationMessagesChannelVersion(context);
    int newVersion                          = existingVersion + 1;

    Log.i(TAG, "Updating message channel from version " + existingVersion + " to " + newVersion);
    if (updateExistingChannel(notificationManager, getMessagesChannelId(existingVersion), getMessagesChannelId(newVersion), updater)) {
      TextSecurePreferences.setNotificationMessagesChannelVersion(context, newVersion);
    } else {
      onCreate(context, notificationManager);
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
