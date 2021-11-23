package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.TranslationDetection;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment;
import org.thoughtcrime.securesms.database.model.MegaphoneRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.SignalPinReminderDialog;
import org.thoughtcrime.securesms.lock.SignalPinReminders;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.KbsMigrationActivity;
import org.thoughtcrime.securesms.messagerequests.MessageRequestMegaphoneActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.manage.ManageProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LocaleFeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.VersionTracker;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperActivity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Creating a new megaphone:
 * - Add an enum to {@link Event}
 * - Return a megaphone in {@link #forRecord(Context, MegaphoneRecord)}
 * - Include the event in {@link #buildDisplayOrder(Context)}
 *
 * Common patterns:
 * - For events that have a snooze-able recurring display schedule, use a {@link RecurringSchedule}.
 * - For events guarded by feature flags, set a {@link ForeverSchedule} with false in
 *   {@link #buildDisplayOrder(Context)}.
 * - For events that change, return different megaphones in {@link #forRecord(Context, MegaphoneRecord)}
 *   based on whatever properties you're interested in.
 */
public final class Megaphones {

  private static final String TAG = Log.tag(Megaphones.class);

  private static final MegaphoneSchedule ALWAYS = new ForeverSchedule(true);
  private static final MegaphoneSchedule NEVER  = new ForeverSchedule(false);

  private Megaphones() {}

  static @Nullable Megaphone getNextMegaphone(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
    long currentTime = System.currentTimeMillis();

    List<Megaphone> megaphones = Stream.of(buildDisplayOrder(context))
                                       .filter(e -> {
                                         MegaphoneRecord   record = Objects.requireNonNull(records.get(e.getKey()));
                                         MegaphoneSchedule schedule = e.getValue();

                                         return !record.isFinished() && schedule.shouldDisplay(record.getSeenCount(), record.getLastSeen(), record.getFirstVisible(), currentTime);
                                       })
                                       .map(Map.Entry::getKey)
                                       .map(records::get)
                                       .map(record -> Megaphones.forRecord(context, record))
                                       .sortBy(m -> -m.getPriority().getPriorityValue())
                                       .toList();

    if (megaphones.size() > 0) {
      return megaphones.get(0);
    } else {
      return null;
    }
  }

  /**
   * This is when you would hide certain megaphones based on {@link FeatureFlags}. You could
   * conditionally set a {@link ForeverSchedule} set to false for disabled features.
   */
  private static Map<Event, MegaphoneSchedule> buildDisplayOrder(@NonNull Context context) {
    return new LinkedHashMap<Event, MegaphoneSchedule>() {{
      put(Event.REACTIONS, ALWAYS);
      put(Event.PINS_FOR_ALL, new PinsForAllSchedule());
      put(Event.PIN_REMINDER, new SignalPinReminderSchedule());
      put(Event.MESSAGE_REQUESTS, shouldShowMessageRequestsMegaphone() ? ALWAYS : NEVER);
      put(Event.LINK_PREVIEWS, shouldShowLinkPreviewsMegaphone(context) ? ALWAYS : NEVER);
      put(Event.CLIENT_DEPRECATED, SignalStore.misc().isClientDeprecated() ? ALWAYS : NEVER);
      put(Event.RESEARCH, shouldShowResearchMegaphone(context) ? ShowForDurationSchedule.showForDays(7) : NEVER);
      put(Event.GROUP_CALLING, shouldShowGroupCallingMegaphone() ? ALWAYS : NEVER);
      put(Event.ONBOARDING, shouldShowOnboardingMegaphone(context) ? ALWAYS : NEVER);
      put(Event.NOTIFICATIONS, shouldShowNotificationsMegaphone(context) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(30)) : NEVER);
      put(Event.CHAT_COLORS, ALWAYS);
      put(Event.ADD_A_PROFILE_PHOTO, shouldShowAddAProfilePhotoMegaphone(context) ? ALWAYS : NEVER);
      put(Event.BECOME_A_SUSTAINER, shouldShowDonateMegaphone(context) ? ShowForDurationSchedule.showForDays(7) : NEVER);
    }};
  }

  private static @NonNull Megaphone forRecord(@NonNull Context context, @NonNull MegaphoneRecord record) {
    switch (record.getEvent()) {
      case REACTIONS:
        return buildReactionsMegaphone();
      case PINS_FOR_ALL:
        return buildPinsForAllMegaphone(record);
      case PIN_REMINDER:
        return buildPinReminderMegaphone(context);
      case MESSAGE_REQUESTS:
        return buildMessageRequestsMegaphone(context);
      case LINK_PREVIEWS:
        return buildLinkPreviewsMegaphone();
      case CLIENT_DEPRECATED:
        return buildClientDeprecatedMegaphone(context);
      case RESEARCH:
        return buildResearchMegaphone(context);
      case GROUP_CALLING:
        return buildGroupCallingMegaphone(context);
      case ONBOARDING:
        return buildOnboardingMegaphone();
      case NOTIFICATIONS:
        return buildNotificationsMegaphone(context);
      case CHAT_COLORS:
        return buildChatColorsMegaphone(context);
      case ADD_A_PROFILE_PHOTO:
        return buildAddAProfilePhotoMegaphone(context);
      case BECOME_A_SUSTAINER:
        return buildBecomeASustainerMegaphone(context);
      default:
        throw new IllegalArgumentException("Event not handled!");
    }
  }

  private static @NonNull Megaphone buildReactionsMegaphone() {
    return new Megaphone.Builder(Event.REACTIONS, Megaphone.Style.REACTIONS)
                        .setPriority(Megaphone.Priority.DEFAULT)
                        .build();
  }

  private static @NonNull Megaphone buildPinsForAllMegaphone(@NonNull MegaphoneRecord record) {
    if (PinsForAllSchedule.shouldDisplayFullScreen(record.getFirstVisible(), System.currentTimeMillis())) {
      return new Megaphone.Builder(Event.PINS_FOR_ALL, Megaphone.Style.FULLSCREEN)
                          .setPriority(Megaphone.Priority.HIGH)
                          .enableSnooze(null)
                          .setOnVisibleListener((megaphone, listener) -> {
                            if (new NetworkConstraint.Factory(ApplicationDependencies.getApplication()).create().isMet()) {
                              listener.onMegaphoneNavigationRequested(KbsMigrationActivity.createIntent(), KbsMigrationActivity.REQUEST_NEW_PIN);
                            }
                          })
                          .build();
    } else {
      return new Megaphone.Builder(Event.PINS_FOR_ALL, Megaphone.Style.BASIC)
                          .setPriority(Megaphone.Priority.HIGH)
                          .setImage(R.drawable.kbs_pin_megaphone)
                          .setTitle(R.string.KbsMegaphone__create_a_pin)
                          .setBody(R.string.KbsMegaphone__pins_keep_information_thats_stored_with_signal_encrytped)
                          .setActionButton(R.string.KbsMegaphone__create_pin, (megaphone, listener) -> {
                            Intent intent = CreateKbsPinActivity.getIntentForPinCreate(ApplicationDependencies.getApplication());

                            listener.onMegaphoneNavigationRequested(intent, CreateKbsPinActivity.REQUEST_NEW_PIN);
                          })
                          .build();
    }
  }

  @SuppressWarnings("CodeBlock2Expr")
  private static @NonNull Megaphone buildPinReminderMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.PIN_REMINDER, Megaphone.Style.BASIC)
                        .setTitle(R.string.Megaphones_verify_your_signal_pin)
                        .setBody(R.string.Megaphones_well_occasionally_ask_you_to_verify_your_pin)
                        .setImage(R.drawable.kbs_pin_megaphone)
                        .setActionButton(R.string.Megaphones_verify_pin, (megaphone, controller) -> {
                          SignalPinReminderDialog.show(controller.getMegaphoneActivity(), controller::onMegaphoneNavigationRequested, new SignalPinReminderDialog.Callback() {
                            @Override
                            public void onReminderDismissed(boolean includedFailure) {
                              Log.i(TAG, "[PinReminder] onReminderDismissed(" + includedFailure + ")");
                              if (includedFailure) {
                                SignalStore.pinValues().onEntrySkipWithWrongGuess();
                              }
                            }

                            @Override
                            public void onReminderCompleted(@NonNull String pin, boolean includedFailure) {
                              Log.i(TAG, "[PinReminder] onReminderCompleted(" + includedFailure + ")");
                              if (includedFailure) {
                                SignalStore.pinValues().onEntrySuccessWithWrongGuess(pin);
                              } else {
                                SignalStore.pinValues().onEntrySuccess(pin);
                              }

                              controller.onMegaphoneSnooze(Event.PIN_REMINDER);
                              controller.onMegaphoneToastRequested(context.getString(SignalPinReminders.getReminderString(SignalStore.pinValues().getCurrentInterval())));
                            }
                          });
                        })
                        .build();
  }

  @SuppressWarnings("CodeBlock2Expr")
  private static @NonNull Megaphone buildMessageRequestsMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.MESSAGE_REQUESTS, Megaphone.Style.FULLSCREEN)
                        .disableSnooze()
                        .setPriority(Megaphone.Priority.HIGH)
                        .setOnVisibleListener(((megaphone, listener) -> {
                          listener.onMegaphoneNavigationRequested(new Intent(context, MessageRequestMegaphoneActivity.class),
                                                                  ConversationListFragment.MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME);
                        }))
                        .build();
  }

  private static @NonNull Megaphone buildLinkPreviewsMegaphone() {
    return new Megaphone.Builder(Event.LINK_PREVIEWS, Megaphone.Style.LINK_PREVIEWS)
                        .setPriority(Megaphone.Priority.HIGH)
                        .build();
  }

  private static @NonNull Megaphone buildClientDeprecatedMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.CLIENT_DEPRECATED, Megaphone.Style.FULLSCREEN)
                        .disableSnooze()
                        .setPriority(Megaphone.Priority.HIGH)
                        .setOnVisibleListener((megaphone, listener) -> listener.onMegaphoneNavigationRequested(new Intent(context, ClientDeprecatedActivity.class)))
                        .build();
  }

  private static @NonNull Megaphone buildResearchMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.RESEARCH, Megaphone.Style.BASIC)
                        .disableSnooze()
                        .setTitle(R.string.ResearchMegaphone_tell_signal_what_you_think)
                        .setBody(R.string.ResearchMegaphone_to_make_signal_the_best_messaging_app_on_the_planet)
                        .setImage(R.drawable.ic_research_megaphone)
                        .setActionButton(R.string.ResearchMegaphone_learn_more, (megaphone, controller) -> {
                          controller.onMegaphoneCompleted(megaphone.getEvent());
                          controller.onMegaphoneDialogFragmentRequested(new ResearchMegaphoneDialog());
                        })
                        .setSecondaryButton(R.string.ResearchMegaphone_dismiss, (megaphone, controller) -> controller.onMegaphoneCompleted(megaphone.getEvent()))
                        .setPriority(Megaphone.Priority.DEFAULT)
                        .build();
  }

  private static @NonNull Megaphone buildGroupCallingMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.GROUP_CALLING, Megaphone.Style.BASIC)
                        .disableSnooze()
                        .setTitle(R.string.GroupCallingMegaphone__introducing_group_calls)
                        .setBody(R.string.GroupCallingMegaphone__open_a_new_group_to_start)
                        .setImage(R.drawable.ic_group_calls_megaphone)
                        .setActionButton(android.R.string.ok, (megaphone, controller) -> {
                          controller.onMegaphoneCompleted(megaphone.getEvent());
                        })
                        .setPriority(Megaphone.Priority.DEFAULT)
                        .build();
  }

  private static @NonNull Megaphone buildOnboardingMegaphone() {
    return new Megaphone.Builder(Event.ONBOARDING, Megaphone.Style.ONBOARDING)
                        .setPriority(Megaphone.Priority.DEFAULT)
                        .build();
  }

  private static @NonNull Megaphone buildNotificationsMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.NOTIFICATIONS, Megaphone.Style.BASIC)
                        .setTitle(R.string.NotificationsMegaphone_turn_on_notifications)
                        .setBody(R.string.NotificationsMegaphone_never_miss_a_message)
                        .setImage(R.drawable.megaphone_notifications_64)
                        .setActionButton(R.string.NotificationsMegaphone_turn_on, (megaphone, controller) -> {
                          if (Build.VERSION.SDK_INT >= 26 && !NotificationChannels.isMessageChannelEnabled(context)) {
                            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.getMessagesChannel(context));
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                            controller.onMegaphoneNavigationRequested(intent);
                          } else if (Build.VERSION.SDK_INT >= 26 &&
                                     (!NotificationChannels.areNotificationsEnabled(context) || !NotificationChannels.isMessagesChannelGroupEnabled(context)))
                          {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                            controller.onMegaphoneNavigationRequested(intent);
                          } else {
                            controller.onMegaphoneNavigationRequested(AppSettingsActivity.notifications(context));
                          }
                        })
                        .setSecondaryButton(R.string.NotificationsMegaphone_not_now, (megaphone, controller) -> controller.onMegaphoneSnooze(Event.NOTIFICATIONS))
                        .setPriority(Megaphone.Priority.DEFAULT)
                        .build();
  }

  private static @NonNull Megaphone buildChatColorsMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.CHAT_COLORS, Megaphone.Style.BASIC)
                        .setTitle(R.string.ChatColorsMegaphone__new_chat_colors)
                        .setBody(R.string.ChatColorsMegaphone__we_switched_up_chat_colors)
                        .setLottie(R.raw.color_bubble_64)
                        .setActionButton(R.string.ChatColorsMegaphone__appearance, (megaphone, listener) -> {
                          listener.onMegaphoneNavigationRequested(ChatWallpaperActivity.createIntent(context));
                          listener.onMegaphoneCompleted(Event.CHAT_COLORS);
                        })
                        .setSecondaryButton(R.string.ChatColorsMegaphone__not_now, (megaphone, listener) -> {
                          listener.onMegaphoneCompleted(Event.CHAT_COLORS);
                        })
                        .build();
  }

  private static @NonNull Megaphone buildAddAProfilePhotoMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.ADD_A_PROFILE_PHOTO, Megaphone.Style.BASIC)
                        .setTitle(R.string.AddAProfilePhotoMegaphone__add_a_profile_photo)
                        .setImage(R.drawable.ic_add_a_profile_megaphone_image)
                        .setBody(R.string.AddAProfilePhotoMegaphone__choose_a_look_and_color)
                        .setActionButton(R.string.AddAProfilePhotoMegaphone__add_photo, (megaphone, listener) -> {
                          listener.onMegaphoneNavigationRequested(ManageProfileActivity.getIntentForAvatarEdit(context));
                          listener.onMegaphoneCompleted(Event.ADD_A_PROFILE_PHOTO);
                        })
                        .setSecondaryButton(R.string.AddAProfilePhotoMegaphone__not_now, (megaphone, listener) -> {
                          listener.onMegaphoneCompleted(Event.ADD_A_PROFILE_PHOTO);
                        })
                        .build();
  }

  private static @NonNull Megaphone buildBecomeASustainerMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.BECOME_A_SUSTAINER, Megaphone.Style.BASIC)
        .setTitle(R.string.BecomeASustainerMegaphone__become_a_sustainer)
        .setImage(R.drawable.ic_become_a_sustainer_megaphone)
        .setBody(R.string.BecomeASustainerMegaphone__signal_is_powered)
        .setActionButton(R.string.BecomeASustainerMegaphone__contribute, (megaphone, listener) -> {
          listener.onMegaphoneNavigationRequested(AppSettingsActivity.subscriptions(context));
          listener.onMegaphoneCompleted(Event.BECOME_A_SUSTAINER);
        })
        .setSecondaryButton(R.string.BecomeASustainerMegaphone__no_thanks, (megaphone, listener) -> {
          listener.onMegaphoneCompleted(Event.BECOME_A_SUSTAINER);
        })
        .build();
  }


  private static boolean shouldShowMessageRequestsMegaphone() {
    return Recipient.self().getProfileName() == ProfileName.EMPTY;
  }

  private static boolean shouldShowResearchMegaphone(@NonNull Context context) {
    return VersionTracker.getDaysSinceFirstInstalled(context) > 7 && LocaleFeatureFlags.isInResearchMegaphone();
  }

  private static boolean shouldShowDonateMegaphone(@NonNull Context context) {
    return VersionTracker.getDaysSinceFirstInstalled(context) > 7 && LocaleFeatureFlags.isInDonateMegaphone() &&
           Recipient.self()
                     .getBadges()
                     .stream()
                     .filter(Objects::nonNull)
                     .noneMatch(badge -> badge.getCategory() == Badge.Category.Donor);
  }

  private static boolean shouldShowLinkPreviewsMegaphone(@NonNull Context context) {
    return TextSecurePreferences.wereLinkPreviewsEnabled(context) && !SignalStore.settings().isLinkPreviewsEnabled();
  }

  private static boolean shouldShowGroupCallingMegaphone() {
    return Build.VERSION.SDK_INT > 19;
  }

  private static boolean shouldShowOnboardingMegaphone(@NonNull Context context) {
    return SignalStore.onboarding().hasOnboarding(context);
  }

  private static boolean shouldShowNotificationsMegaphone(@NonNull Context context) {
    boolean shouldShow = !SignalStore.settings().isMessageNotificationsEnabled() ||
                         !NotificationChannels.isMessageChannelEnabled(context) ||
                         !NotificationChannels.isMessagesChannelGroupEnabled(context) ||
                         !NotificationChannels.areNotificationsEnabled(context);
    if (shouldShow) {
      Locale locale = DynamicLanguageContextWrapper.getUsersSelectedLocale(context);
      if (!new TranslationDetection(context, locale)
               .textExistsInUsersLanguage(R.string.NotificationsMegaphone_turn_on_notifications,
                                          R.string.NotificationsMegaphone_never_miss_a_message,
                                          R.string.NotificationsMegaphone_turn_on,
                                          R.string.NotificationsMegaphone_not_now)) {
        Log.i(TAG, "Would show NotificationsMegaphone but is not yet translated in " + locale);
        return false;
      }
    }
    return shouldShow;
  }

  private static boolean shouldShowAddAProfilePhotoMegaphone(@NonNull Context context) {
    if (SignalStore.misc().hasEverHadAnAvatar()) {
      return false;
    }

    boolean hasAnAvatar = AvatarHelper.hasAvatar(context, Recipient.self().getId());
    if (hasAnAvatar) {
      SignalStore.misc().markHasEverHadAnAvatar();
      return false;
    }

    return true;
  }

  public enum Event {
    REACTIONS("reactions"),
    PINS_FOR_ALL("pins_for_all"),
    PIN_REMINDER("pin_reminder"),
    MESSAGE_REQUESTS("message_requests"),
    LINK_PREVIEWS("link_previews"),
    CLIENT_DEPRECATED("client_deprecated"),
    RESEARCH("research"),
    GROUP_CALLING("group_calling"),
    ONBOARDING("onboarding"),
    NOTIFICATIONS("notifications"),
    CHAT_COLORS("chat_colors"),
    ADD_A_PROFILE_PHOTO("add_a_profile_photo"),
    BECOME_A_SUSTAINER("become_a_sustainer");

    private final String key;

    Event(@NonNull String key) {
      this.key = key;
    }

    public @NonNull String getKey() {
      return key;
    }

    public static Event fromKey(@NonNull String key) {
      for (Event event : values()) {
        if (event.getKey().equals(key)) {
          return event;
        }
      }
      throw new IllegalArgumentException("No event for key: " + key);
    }

    public static boolean hasKey(@NonNull String key) {
      for (Event event : values()) {
        if (event.getKey().equals(key)) {
          return true;
        }
      }
      return false;
    }
  }
}
