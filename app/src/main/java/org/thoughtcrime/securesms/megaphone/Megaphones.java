package org.thoughtcrime.securesms.megaphone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.SetUtil;
import org.signal.core.util.TranslationDetection;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations;
import org.thoughtcrime.securesms.database.model.MegaphoneRecord;
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.keyvalue.SmsExportPhase;
import org.thoughtcrime.securesms.lock.SignalPinReminderDialog;
import org.thoughtcrime.securesms.lock.SignalPinReminders;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.KbsMigrationActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.manage.ManageProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LocaleFeatureFlags;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.VersionTracker;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Creating a new megaphone:
 * - Add an enum to {@link Event}
 * - Return a megaphone in {@link #forRecord(Context, MegaphoneRecord)}
 * - Include the event in {@link #buildDisplayOrder(Context, Map)}
 *
 * Common patterns:
 * - For events that have a snooze-able recurring display schedule, use a {@link RecurringSchedule}.
 * - For events guarded by feature flags, set a {@link ForeverSchedule} with false in
 *   {@link #buildDisplayOrder(Context, Map)}.
 * - For events that change, return different megaphones in {@link #forRecord(Context, MegaphoneRecord)}
 *   based on whatever properties you're interested in.
 */
public final class Megaphones {

  private static final String TAG = Log.tag(Megaphones.class);

  private static final MegaphoneSchedule ALWAYS = new ForeverSchedule(true);
  private static final MegaphoneSchedule NEVER  = new ForeverSchedule(false);

  private static final Set<Event> DONATE_EVENTS                      = SetUtil.newHashSet(Event.BECOME_A_SUSTAINER, Event.DONATE_Q2_2022);
  private static final long       MIN_TIME_BETWEEN_DONATE_MEGAPHONES = TimeUnit.DAYS.toMillis(30);

  private Megaphones() {}

  @WorkerThread
  static @Nullable Megaphone getNextMegaphone(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
    long currentTime = System.currentTimeMillis();

    List<Megaphone> megaphones = Stream.of(buildDisplayOrder(context, records))
                                       .filter(e -> {
                                         MegaphoneRecord   record = Objects.requireNonNull(records.get(e.getKey()));
                                         MegaphoneSchedule schedule = e.getValue();

                                         return !record.isFinished() && schedule.shouldDisplay(record.getSeenCount(), record.getLastSeen(), record.getFirstVisible(), currentTime);
                                       })
                                       .map(Map.Entry::getKey)
                                       .map(records::get)
                                       .map(record -> Megaphones.forRecord(context, record))
                                       .toList();

    if (megaphones.size() > 0) {
      return megaphones.get(0);
    } else {
      return null;
    }
  }

  /**
   * The megaphones we want to display *in priority order*. This is a {@link LinkedHashMap}, so order is preserved.
   * We will render the first applicable megaphone in this collection.
   *
   * This is also when you would hide certain megaphones based on things like {@link FeatureFlags}.
   */
  private static Map<Event, MegaphoneSchedule> buildDisplayOrder(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
    return new LinkedHashMap<Event, MegaphoneSchedule>() {{
      put(Event.PINS_FOR_ALL, new PinsForAllSchedule());
      put(Event.CLIENT_DEPRECATED, SignalStore.misc().isClientDeprecated() ? ALWAYS : NEVER);
      put(Event.NOTIFICATIONS, shouldShowNotificationsMegaphone(context) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(30)) : NEVER);
      put(Event.SMS_EXPORT, new SmsExportReminderSchedule(context));
      put(Event.BACKUP_SCHEDULE_PERMISSION, shouldShowBackupSchedulePermissionMegaphone(context) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(3)) : NEVER);
      put(Event.ONBOARDING, shouldShowOnboardingMegaphone(context) ? ALWAYS : NEVER);
      put(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION, shouldShowTurnOffCircumventionMegaphone() ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(7)) : NEVER);
      put(Event.DONATE_Q2_2022, shouldShowDonateMegaphone(context, Event.DONATE_Q2_2022, records) ? ShowForDurationSchedule.showForDays(7) : NEVER);
      put(Event.REMOTE_MEGAPHONE, shouldShowRemoteMegaphone(records) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(1)) : NEVER);
      put(Event.PIN_REMINDER, new SignalPinReminderSchedule());

      // Feature-introduction megaphones should *probably* be added below this divider
      put(Event.ADD_A_PROFILE_PHOTO, shouldShowAddAProfilePhotoMegaphone(context) ? ALWAYS : NEVER);
    }};
  }

  private static @NonNull Megaphone forRecord(@NonNull Context context, @NonNull MegaphoneRecord record) {
    switch (record.getEvent()) {
      case PINS_FOR_ALL:
        return buildPinsForAllMegaphone(record);
      case PIN_REMINDER:
        return buildPinReminderMegaphone(context);
      case CLIENT_DEPRECATED:
        return buildClientDeprecatedMegaphone(context);
      case ONBOARDING:
        return buildOnboardingMegaphone();
      case NOTIFICATIONS:
        return buildNotificationsMegaphone(context);
      case ADD_A_PROFILE_PHOTO:
        return buildAddAProfilePhotoMegaphone(context);
      case BECOME_A_SUSTAINER:
        return buildBecomeASustainerMegaphone(context);
      case DONATE_Q2_2022:
        return buildDonateQ2Megaphone(context);
      case TURN_OFF_CENSORSHIP_CIRCUMVENTION:
        return buildTurnOffCircumventionMegaphone(context);
      case REMOTE_MEGAPHONE:
        return buildRemoteMegaphone(context);
      case BACKUP_SCHEDULE_PERMISSION:
        return buildBackupPermissionMegaphone(context);
      case SMS_EXPORT:
        return buildSmsExportMegaphone(context);
      default:
        throw new IllegalArgumentException("Event not handled!");
    }
  }

  private static @NonNull Megaphone buildPinsForAllMegaphone(@NonNull MegaphoneRecord record) {
    if (PinsForAllSchedule.shouldDisplayFullScreen(record.getFirstVisible(), System.currentTimeMillis())) {
      return new Megaphone.Builder(Event.PINS_FOR_ALL, Megaphone.Style.FULLSCREEN)
                          .enableSnooze(null)
                          .setOnVisibleListener((megaphone, listener) -> {
                            if (new NetworkConstraint.Factory(ApplicationDependencies.getApplication()).create().isMet()) {
                              listener.onMegaphoneNavigationRequested(KbsMigrationActivity.createIntent(), KbsMigrationActivity.REQUEST_NEW_PIN);
                            }
                          })
                          .build();
    } else {
      return new Megaphone.Builder(Event.PINS_FOR_ALL, Megaphone.Style.BASIC)
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
                              controller.onMegaphoneToastRequested(controller.getMegaphoneActivity().getString(SignalPinReminders.getReminderString(SignalStore.pinValues().getCurrentInterval())));
                            }
                          });
                        })
                        .build();
  }

  private static @NonNull Megaphone buildClientDeprecatedMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.CLIENT_DEPRECATED, Megaphone.Style.FULLSCREEN)
                        .disableSnooze()
                        .setOnVisibleListener((megaphone, listener) -> listener.onMegaphoneNavigationRequested(new Intent(context, ClientDeprecatedActivity.class)))
                        .build();
  }

  private static @NonNull Megaphone buildOnboardingMegaphone() {
    return new Megaphone.Builder(Event.ONBOARDING, Megaphone.Style.ONBOARDING)
                        .build();
  }

  private static @NonNull Megaphone buildNotificationsMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.NOTIFICATIONS, Megaphone.Style.BASIC)
                        .setTitle(R.string.NotificationsMegaphone_turn_on_notifications)
                        .setBody(R.string.NotificationsMegaphone_never_miss_a_message)
                        .setImage(R.drawable.megaphone_notifications_64)
                        .setActionButton(R.string.NotificationsMegaphone_turn_on, (megaphone, controller) -> {
                          if (Build.VERSION.SDK_INT >= 26 && !NotificationChannels.getInstance().isMessageChannelEnabled()) {
                            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.getInstance().getMessagesChannel());
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                            controller.onMegaphoneNavigationRequested(intent);
                          } else if (Build.VERSION.SDK_INT >= 26 &&
                                     (!NotificationChannels.getInstance().areNotificationsEnabled() || !NotificationChannels.getInstance().isMessagesChannelGroupEnabled()))
                          {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                            controller.onMegaphoneNavigationRequested(intent);
                          } else {
                            controller.onMegaphoneNavigationRequested(AppSettingsActivity.notifications(context));
                          }
                        })
                        .setSecondaryButton(R.string.NotificationsMegaphone_not_now, (megaphone, controller) -> controller.onMegaphoneSnooze(Event.NOTIFICATIONS))
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
        .setBody(R.string.BecomeASustainerMegaphone__signal_is_powered_by)
        .setActionButton(R.string.BecomeASustainerMegaphone__donate, (megaphone, listener) -> {
          listener.onMegaphoneNavigationRequested(AppSettingsActivity.subscriptions(context));
          listener.onMegaphoneCompleted(Event.BECOME_A_SUSTAINER);
        })
        .setSecondaryButton(R.string.BecomeASustainerMegaphone__not_now, (megaphone, listener) -> {
          listener.onMegaphoneCompleted(Event.BECOME_A_SUSTAINER);
        })
        .build();
  }

  private static @NonNull Megaphone buildDonateQ2Megaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.DONATE_Q2_2022, Megaphone.Style.BASIC)
        .setTitle(R.string.Donate2022Q2Megaphone_donate_to_signal)
        .setImage(R.drawable.ic_donate_q2_2022)
        .setBody(R.string.Donate2022Q2Megaphone_signal_is_powered_by_people_like_you)
        .setActionButton(R.string.Donate2022Q2Megaphone_donate, (megaphone, listener) -> {
          listener.onMegaphoneNavigationRequested(AppSettingsActivity.subscriptions(context));
          listener.onMegaphoneCompleted(Event.DONATE_Q2_2022);
        })
        .setSecondaryButton(R.string.Donate2022Q2Megaphone_not_now, (megaphone, listener) -> {
          listener.onMegaphoneCompleted(Event.DONATE_Q2_2022);
        })
        .build();
  }

  private static @NonNull Megaphone buildTurnOffCircumventionMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION, Megaphone.Style.BASIC)
        .setTitle(R.string.CensorshipCircumventionMegaphone_turn_off_censorship_circumvention)
        .setImage(R.drawable.ic_censorship_megaphone_64)
        .setBody(R.string.CensorshipCircumventionMegaphone_you_can_now_connect_to_the_signal_service)
        .setActionButton(R.string.CensorshipCircumventionMegaphone_turn_off, (megaphone, listener) -> {
          SignalStore.settings().setCensorshipCircumventionEnabled(false);
          listener.onMegaphoneSnooze(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION);
        })
        .setSecondaryButton(R.string.CensorshipCircumventionMegaphone_no_thanks, (megaphone, listener) -> {
          listener.onMegaphoneSnooze(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION);
        })
        .build();
  }

  private static @NonNull Megaphone buildRemoteMegaphone(@NonNull Context context) {
    RemoteMegaphoneRecord record = RemoteMegaphoneRepository.getRemoteMegaphoneToShow(System.currentTimeMillis());

    if (record != null) {
      Megaphone.Builder builder = new Megaphone.Builder(Event.REMOTE_MEGAPHONE, Megaphone.Style.BASIC)
          .setTitle(record.getTitle())
          .setBody(record.getBody());

      if (record.getImageUri() != null) {
        builder.setImageRequest(GlideApp.with(context).asDrawable().load(record.getImageUri()));
      }

      if (record.hasPrimaryAction()) {
        //noinspection ConstantConditions
        builder.setActionButton(record.getPrimaryActionText(), (megaphone, controller) -> {
          RemoteMegaphoneRepository.getAction(Objects.requireNonNull(record.getPrimaryActionId()))
                                   .run(context, controller, record);
        });
      }

      if (record.hasSecondaryAction()) {
        //noinspection ConstantConditions
        builder.setSecondaryButton(record.getSecondaryActionText(), (megaphone, controller) -> {
          RemoteMegaphoneRepository.getAction(Objects.requireNonNull(record.getSecondaryActionId()))
                                   .run(context, controller, record);
        });
      }

      builder.setOnVisibleListener((megaphone, controller) -> {
        RemoteMegaphoneRepository.markShown(record.getUuid());
      });

      return builder.build();
    } else {
      throw new IllegalStateException("No record to show");
    }
  }

  @SuppressLint("InlinedApi")
  private static Megaphone buildBackupPermissionMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.BACKUP_SCHEDULE_PERMISSION, Megaphone.Style.BASIC)
        .setTitle(R.string.BackupSchedulePermissionMegaphone__cant_back_up_chats)
        .setImage(R.drawable.ic_cant_backup_megaphone)
        .setBody(R.string.BackupSchedulePermissionMegaphone__your_chats_are_no_longer_being_automatically_backed_up)
        .setActionButton(R.string.BackupSchedulePermissionMegaphone__back_up_chats, (megaphone, controller) -> {
          controller.onMegaphoneDialogFragmentRequested(new ReenableBackupsDialogFragment());
        })
        .setSecondaryButton(R.string.BackupSchedulePermissionMegaphone__not_now, (megaphone, controller) -> {
          controller.onMegaphoneSnooze(Event.BACKUP_SCHEDULE_PERMISSION);
        })
        .build();
  }

  private static @NonNull Megaphone buildSmsExportMegaphone(@NonNull Context context) {
    SmsExportPhase phase = SignalStore.misc().getSmsExportPhase();

    if (phase == SmsExportPhase.PHASE_1) {
      return new Megaphone.Builder(Event.SMS_EXPORT, Megaphone.Style.BASIC)
          .setTitle(R.string.SmsExportMegaphone__sms_support_going_away)
          .setImage(R.drawable.sms_megaphone)
          .setBody(R.string.SmsExportMegaphone__dont_worry_encrypted_signal_messages_will_continue_to_work)
          .setActionButton(R.string.SmsExportMegaphone__continue, (megaphone, controller) -> {
            controller.onMegaphoneSnooze(Event.SMS_EXPORT);
            controller.onMegaphoneNavigationRequested(SmsExportActivity.createIntent(context, true), SmsExportMegaphoneActivity.REQUEST_CODE);
          })
          .setSecondaryButton(R.string.Megaphones_remind_me_later, (megaphone, controller) -> controller.onMegaphoneSnooze(Event.SMS_EXPORT))
          .setOnVisibleListener((megaphone, controller) -> SignalStore.misc().startSmsPhase1())
          .build();
    } else {
      Megaphone.Builder builder = new Megaphone.Builder(Event.SMS_EXPORT, Megaphone.Style.FULLSCREEN)
          .setOnVisibleListener((megaphone, controller) -> {
            if (phase.isBlockingUi()) {
              SmsExportReminderSchedule.setShowPhase3Megaphone(false);
            }
            controller.onMegaphoneNavigationRequested(new Intent(context, SmsExportMegaphoneActivity.class), SmsExportMegaphoneActivity.REQUEST_CODE);
          });

      if (phase.isBlockingUi()) {
        builder.disableSnooze();
      }

      return builder.build();
    }
  }

  private static boolean shouldShowDonateMegaphone(@NonNull Context context, @NonNull Event event, @NonNull Map<Event, MegaphoneRecord> records) {
    long timeSinceLastDonatePrompt = timeSinceLastDonatePrompt(event, records);

    return timeSinceLastDonatePrompt > MIN_TIME_BETWEEN_DONATE_MEGAPHONES &&
           VersionTracker.getDaysSinceFirstInstalled(context) >= 7 &&
           LocaleFeatureFlags.isInDonateMegaphone() &&
           InAppDonations.INSTANCE.hasAtLeastOnePaymentMethodAvailable() &&
           Recipient.self()
                    .getBadges()
                    .stream()
                    .filter(Objects::nonNull)
                    .noneMatch(badge -> badge.getCategory() == Badge.Category.Donor);
  }

  private static boolean shouldShowOnboardingMegaphone(@NonNull Context context) {
    return SignalStore.onboarding().hasOnboarding(context);
  }

  private static boolean shouldShowTurnOffCircumventionMegaphone() {
    return ApplicationDependencies.getSignalServiceNetworkAccess().isCensored() &&
           SignalStore.misc().isServiceReachableWithoutCircumvention();
  }

  private static boolean shouldShowNotificationsMegaphone(@NonNull Context context) {
    boolean shouldShow = !SignalStore.settings().isMessageNotificationsEnabled() ||
                         !NotificationChannels.getInstance().isMessageChannelEnabled() ||
                         !NotificationChannels.getInstance().isMessagesChannelGroupEnabled() ||
                         !NotificationChannels.getInstance().areNotificationsEnabled();
    if (shouldShow) {
      Locale locale = DynamicLanguageContextWrapper.getUsersSelectedLocale(context);
      if (!new TranslationDetection(context, locale)
               .textExistsInUsersLanguage(R.string.NotificationsMegaphone_turn_on_notifications,
                                          R.string.NotificationsMegaphone_never_miss_a_message,
                                          R.string.NotificationsMegaphone_turn_on,
                                          R.string.NotificationsMegaphone_not_now))
      {
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

  @WorkerThread
  private static boolean shouldShowRemoteMegaphone(@NonNull Map<Event, MegaphoneRecord> records) {
    boolean canShowLocalDonate = timeSinceLastDonatePrompt(Event.REMOTE_MEGAPHONE, records) > MIN_TIME_BETWEEN_DONATE_MEGAPHONES;
    return RemoteMegaphoneRepository.hasRemoteMegaphoneToShow(canShowLocalDonate);
  }

  private static boolean shouldShowBackupSchedulePermissionMegaphone(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 31 && SignalStore.settings().isBackupEnabled() && !ServiceUtil.getAlarmManager(context).canScheduleExactAlarms();
  }

  /**
   * Unfortunately lastSeen is only set today upon snoozing, which never happens to donate prompts.
   * So we use firstVisible as a proxy.
   */
  private static long timeSinceLastDonatePrompt(@NonNull Event excludeEvent, @NonNull Map<Event, MegaphoneRecord> records) {
    long lastSeenDonatePrompt = records.entrySet()
                                       .stream()
                                       .filter(e -> DONATE_EVENTS.contains(e.getKey()))
                                       .filter(e -> !e.getKey().equals(excludeEvent))
                                       .map(e -> e.getValue().getFirstVisible())
                                       .filter(t -> t > 0)
                                       .sorted()
                                       .findFirst()
                                       .orElse(0L);
    return System.currentTimeMillis() - lastSeenDonatePrompt;
  }


  public enum Event {
    PINS_FOR_ALL("pins_for_all"),
    PIN_REMINDER("pin_reminder"),
    CLIENT_DEPRECATED("client_deprecated"),
    ONBOARDING("onboarding"),
    NOTIFICATIONS("notifications"),
    ADD_A_PROFILE_PHOTO("add_a_profile_photo"),
    BECOME_A_SUSTAINER("become_a_sustainer"),
    DONATE_Q2_2022("donate_q2_2022"),
    TURN_OFF_CENSORSHIP_CIRCUMVENTION("turn_off_censorship_circumvention"),
    REMOTE_MEGAPHONE("remote_megaphone"),
    BACKUP_SCHEDULE_PERMISSION("backup_schedule_permission"),
    SMS_EXPORT("sms_export");

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
