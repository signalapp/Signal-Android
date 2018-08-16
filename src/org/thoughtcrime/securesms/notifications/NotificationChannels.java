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
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.logging.Log;

import java.util.Arrays;

public class NotificationChannels {

  private static final String TAG = NotificationChannels.class.getSimpleName();

  private static final int VERSION_MESSAGES_CATEGORY = 2;

  private static final int VERSION = 2;

  private static final String CATEGORY_MESSAGES = "messages";
  private static final String CONTACT_PREFIX    = "contact_";
  private static final String MESSAGES_PREFIX   = "messages_";

  public static final String CALLS         = "calls_v2";
  public static final String FAILURES      = "failures";
  public static final String APP_UPDATES   = "app_updates";
  public static final String BACKUPS       = "backups_v2";
  public static final String LOCKED_STATUS = "locked_status_v2";
  public static final String OTHER         = "other_v2";

  /**
   * Ensures all of the notification channels are created. No harm in repeat calls. Call is safely
   * ignored for API < 26.
   */
  public static void create(@NonNull Context context) {
    if (!supported()) {
      return;
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    if (notificationManager == null) {
      Log.w(TAG, "Unable to retrieve notification manager. Can't setup channels.");
      return;
    }

    int oldVersion = TextSecurePreferences.getNotificationChannelVersion(context);
    if (oldVersion != VERSION) {
      onUpgrade(notificationManager, oldVersion, VERSION);
      TextSecurePreferences.setNotificationChannelVersion(context, VERSION);
    }

    onCreate(context, notificationManager);
  }

  /**
   * @return The channel ID for the default messages channel. Prefer
   * {@link Recipient#getNotificationChannel(Context)} if you know the recipient.
   */
  public static @NonNull String getMessagesChannel(@NonNull Context context) {
    return getMessagesChannelId(TextSecurePreferences.getNotificationMessagesChannelVersion(context));
  }

  /**
   * @return Whether or not notification channels are supported.
   */
  public static boolean supported() {
    return Build.VERSION.SDK_INT >= 26;
  }

  public static String getChannelDisplayNameFor(@Nullable String systemName, @Nullable String profileName, @NonNull Address address) {
    return TextUtils.isEmpty(systemName) ? (TextUtils.isEmpty(profileName) ? address.serialize() : profileName) : systemName;
  }

  /**
   * Creates a channel for the specified recipient.
   * @return The channel ID for the newly-created channel.
   */
  public static String createChannelFor(@NonNull Context context, @NonNull Recipient recipient) {
    VibrateState vibrateState     = recipient.getMessageVibrate();
    boolean      vibrationEnabled = vibrateState == VibrateState.DEFAULT ? TextSecurePreferences.isNotificationVibrateEnabled(context) : vibrateState == VibrateState.ENABLED;
    String       displayName      = getChannelDisplayNameFor(recipient.getName(), recipient.getProfileName(), recipient.getAddress());

    return createChannelFor(context, recipient.getAddress(), displayName, recipient.getMessageRingtone(), vibrationEnabled);
  }

  /**
   * More verbose version of {@link #createChannelFor(Context, Recipient)}.
   */
  public static String createChannelFor(@NonNull Context context,
                                        @NonNull Address address,
                                        @NonNull String displayName,
                                        @Nullable Uri messageSound,
                                        boolean vibrationEnabled)
  {
    if (!supported()) {
      return getMessagesChannel(context);
    }

    String              channelId = generateChannelIdFor(address);
    NotificationChannel channel   = new NotificationChannel(channelId, displayName, NotificationManager.IMPORTANCE_HIGH);

    setLedPreference(channel, TextSecurePreferences.getNotificationLedColor(context));
    channel.setGroup(CATEGORY_MESSAGES);
    channel.enableVibration(vibrationEnabled);
    channel.setSound(messageSound, new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                                                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                                                                .build());

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    if (notificationManager == null) {
      Log.w(TAG, "Unable to retrieve notification manager. Cannot create channel for recipient.");
      return channelId;
    }

    notificationManager.createNotificationChannel(channel);

    return channelId;
  }

  /**
   * Deletes the channel generated for the provided recipient. Safe to call even if there was never
   * a channel made for that recipient.
   */
  public static void deleteChannelFor(@NonNull Context context, @NonNull Recipient recipient) {
    if (!supported()) {
      return;
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    if (notificationManager == null) {
      Log.w(TAG, "Unable to retrieve notification manager. Cannot delete channel.");
      return;
    }

    String channel = recipient.getNotificationChannel(context);

    if (!TextUtils.isEmpty(channel) && !getMessagesChannel(context).equals(channel)) {
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
  public static void updateMessagesLedColor(@NonNull Context context, @NonNull String color) {
    if (!supported()) {
      return;
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    if (notificationManager == null) {
      Log.w(TAG, "Unable to retrieve notification manager. Cannot update led color.");
      return;
    }

    updateMessageChannelLedColor(context, notificationManager, color);
    updateAllRecipientChannelLedColors(context, notificationManager, color);
  }

  /**
   * Updates the name of an existing channel to match the recipient's current name. Will have no
   * effect if the recipient doesn't have an existing valid channel.
   */
  public static void updateContactChannelName(@NonNull Context context, @NonNull Recipient recipient) {
    if (!supported() || !recipient.hasCustomNotifications()) {
      return;
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    if (notificationManager == null) {
      Log.w(TAG, "Unable to retrieve notification manager. Cannot update channel name.");
      return;
    }

    if (notificationManager.getNotificationChannel(recipient.getNotificationChannel(context)) == null) {
      Log.w(TAG, "Tried to update the name of a channel, but that channel doesn't exist.");
      return;
    }

    NotificationChannel channel = new NotificationChannel(recipient.getNotificationChannel(context),
                                                          getChannelDisplayNameFor(recipient.getName(), recipient.getProfileName(), recipient.getAddress()),
                                                          NotificationManager.IMPORTANCE_HIGH);
    channel.setGroup(CATEGORY_MESSAGES);
    notificationManager.createNotificationChannel(channel);
  }

  @TargetApi(26)
  private static void onCreate(@NonNull Context context, @NonNull NotificationManager notificationManager) {
    NotificationChannelGroup messagesGroup = new NotificationChannelGroup(CATEGORY_MESSAGES, context.getResources().getString(R.string.NotificationChannel_group_messages));
    notificationManager.createNotificationChannelGroup(messagesGroup);

    NotificationChannel messages     = new NotificationChannel(getMessagesChannel(context), context.getString(R.string.NotificationChannel_messages), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel calls        = new NotificationChannel(CALLS, context.getString(R.string.NotificationChannel_calls), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel failures     = new NotificationChannel(FAILURES, context.getString(R.string.NotificationChannel_failures), NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel backups      = new NotificationChannel(BACKUPS, context.getString(R.string.NotificationChannel_backups), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel lockedStatus = new NotificationChannel(LOCKED_STATUS, context.getString(R.string.NotificationChannel_locked_status), NotificationManager.IMPORTANCE_LOW);
    NotificationChannel other        = new NotificationChannel(OTHER, context.getString(R.string.NotificationChannel_other), NotificationManager.IMPORTANCE_LOW);

    messages.setGroup(CATEGORY_MESSAGES);
    messages.enableVibration(TextSecurePreferences.isNotificationVibrateEnabled(context));
    messages.setSound(TextSecurePreferences.getNotificationRingtone(context), new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                                                                                                           .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                                                                                                           .build());
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

  @TargetApi(26)
  private static void updateMessageChannelLedColor(@NonNull Context context, @NonNull NotificationManager notificationManager, @NonNull String color) {
    int                 existingVersion = TextSecurePreferences.getNotificationMessagesChannelVersion(context);
    NotificationChannel existingChannel = notificationManager.getNotificationChannel(getMessagesChannelId(existingVersion));

    notificationManager.deleteNotificationChannel(existingChannel.getId());

    int                 newVersion = existingVersion + 1;
    NotificationChannel newChannel = copyChannel(existingChannel, getMessagesChannelId(newVersion));

    setLedPreference(newChannel, color);
    notificationManager.createNotificationChannel(newChannel);

    TextSecurePreferences.setNotificationMessagesChannelVersion(context, newVersion);
  }

  @WorkerThread
  @TargetApi(26)
  private static void updateAllRecipientChannelLedColors(@NonNull Context context, @NonNull NotificationManager notificationManager, @NonNull String color) {
    RecipientDatabase database = DatabaseFactory.getRecipientDatabase(context);

    try (RecipientDatabase.RecipientReader recipients = database.getRecipientsWithNotificationChannels()) {
      Recipient recipient;
      while ((recipient = recipients.getNext()) != null) {
        NotificationChannel existingChannel = notificationManager.getNotificationChannel(recipient.getNotificationChannel(context));
        notificationManager.deleteNotificationChannel(existingChannel.getId());

        NotificationChannel newChannel = copyChannel(existingChannel, generateChannelIdFor(recipient.getAddress()));
        newChannel.setGroup(CATEGORY_MESSAGES);
        setLedPreference(newChannel, color);

        database.setNotificationChannel(recipient, newChannel.getId());

        notificationManager.createNotificationChannel(newChannel);
      }
    }
  }
}
