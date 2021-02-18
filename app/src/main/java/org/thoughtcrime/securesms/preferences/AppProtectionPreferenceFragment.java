package org.thoughtcrime.securesms.preferences;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.autofill.HintConstants;
import androidx.core.app.DialogCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.PassphraseChangeActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.blocked.BlockedUsersActivity;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ConversationShortcutUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.PinValues;
import org.thoughtcrime.securesms.keyvalue.SettingsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.PinHashing;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.KbsConstants;
import org.thoughtcrime.securesms.lock.v2.RegistrationLockUtil;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.pin.RegistrationLockV2Dialog;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import mobi.upod.timedurationpicker.TimeDurationPickerDialog;

public class AppProtectionPreferenceFragment extends CorrectedPreferenceFragment {

  private static final String TAG = Log.tag(AppProtectionPreferenceFragment.class);

  private static final String PREFERENCE_CATEGORY_BLOCKED             = "preference_category_blocked";
  private static final String PREFERENCE_UNIDENTIFIED_LEARN_MORE      = "pref_unidentified_learn_more";
  private static final String PREFERENCE_INCOGNITO_LEARN_MORE         = "pref_incognito_learn_more";
  private static final String PREFERENCE_WHO_CAN_SEE_PHONE_NUMBER     = "pref_who_can_see_phone_number";
  private static final String PREFERENCE_WHO_CAN_FIND_BY_PHONE_NUMBER = "pref_who_can_find_by_phone_number";

  private CheckBoxPreference disablePassphrase;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    disablePassphrase = (CheckBoxPreference) this.findPreference("pref_enable_passphrase_temporary");

    this.findPreference(KbsValues.V2_LOCK_ENABLED).setPreferenceDataStore(SignalStore.getPreferenceDataStore());
    ((SwitchPreferenceCompat) this.findPreference(KbsValues.V2_LOCK_ENABLED)).setChecked(SignalStore.kbsValues().isV2RegistrationLockEnabled());
    this.findPreference(KbsValues.V2_LOCK_ENABLED).setOnPreferenceChangeListener(new RegistrationLockV2ChangedListener());

    this.findPreference(PinValues.PIN_REMINDERS_ENABLED).setPreferenceDataStore(SignalStore.getPreferenceDataStore());
    ((SwitchPreferenceCompat) this.findPreference(PinValues.PIN_REMINDERS_ENABLED)).setChecked(SignalStore.pinValues().arePinRemindersEnabled());
    this.findPreference(PinValues.PIN_REMINDERS_ENABLED).setOnPreferenceChangeListener(new PinRemindersChangedListener());

    this.findPreference(TextSecurePreferences.SCREEN_LOCK).setOnPreferenceChangeListener(new ScreenLockListener());
    this.findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT).setOnPreferenceClickListener(new ScreenLockTimeoutListener());

    this.findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF).setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF).setOnPreferenceClickListener(new PassphraseIntervalClickListener());
    this.findPreference(TextSecurePreferences.READ_RECEIPTS_PREF).setOnPreferenceChangeListener(new ReadReceiptToggleListener());
    this.findPreference(TextSecurePreferences.TYPING_INDICATORS).setOnPreferenceChangeListener(new TypingIndicatorsToggleListener());
    this.findPreference(PREFERENCE_CATEGORY_BLOCKED).setOnPreferenceClickListener(new BlockedContactsClickListener());
    this.findPreference(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS).setOnPreferenceChangeListener(new ShowUnidentifiedDeliveryIndicatorsChangedListener());
    this.findPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS).setOnPreferenceChangeListener(new UniversalUnidentifiedAccessChangedListener());
    this.findPreference(PREFERENCE_UNIDENTIFIED_LEARN_MORE).setOnPreferenceClickListener(new UnidentifiedLearnMoreClickListener());
    this.findPreference(PREFERENCE_INCOGNITO_LEARN_MORE).setOnPreferenceClickListener(new IncognitoLearnMoreClickListener());
    disablePassphrase.setOnPreferenceChangeListener(new DisablePassphraseClickListener());

    if (FeatureFlags.phoneNumberPrivacy()) {
      Preference whoCanSeePhoneNumber    = this.findPreference(PREFERENCE_WHO_CAN_SEE_PHONE_NUMBER);
      Preference whoCanFindByPhoneNumber = this.findPreference(PREFERENCE_WHO_CAN_FIND_BY_PHONE_NUMBER);

      whoCanSeePhoneNumber.setPreferenceDataStore(null);
      whoCanSeePhoneNumber.setOnPreferenceClickListener(new PhoneNumberPrivacyWhoCanSeeClickListener());

      whoCanFindByPhoneNumber.setPreferenceDataStore(null);
      whoCanFindByPhoneNumber.setOnPreferenceClickListener(new PhoneNumberPrivacyWhoCanFindClickListener());
    } else {
      this.findPreference("category_phone_number_privacy").setVisible(false);
    }

    SwitchPreferenceCompat linkPreviewPref = (SwitchPreferenceCompat) this.findPreference(SettingsValues.LINK_PREVIEWS);
    linkPreviewPref.setChecked(SignalStore.settings().isLinkPreviewsEnabled());
    linkPreviewPref.setPreferenceDataStore(SignalStore.getPreferenceDataStore());
    linkPreviewPref.setOnPreferenceChangeListener(new LinkPreviewToggleListener());

    initializeVisibility();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_app_protection);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__privacy);

    if (!TextSecurePreferences.isPasswordDisabled(getContext())) initializePassphraseTimeoutSummary();
    else                                                         initializeScreenLockTimeoutSummary();

    disablePassphrase.setChecked(!TextSecurePreferences.isPasswordDisabled(getActivity()));

    Preference             signalPinCreateChange   = this.findPreference(TextSecurePreferences.SIGNAL_PIN_CHANGE);
    SwitchPreferenceCompat signalPinReminders      = (SwitchPreferenceCompat) this.findPreference(PinValues.PIN_REMINDERS_ENABLED);
    SwitchPreferenceCompat registrationLockV2      = (SwitchPreferenceCompat) this.findPreference(KbsValues.V2_LOCK_ENABLED);

    if (SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut()) {
      signalPinCreateChange.setOnPreferenceClickListener(new KbsPinUpdateListener());
      signalPinCreateChange.setTitle(R.string.preferences_app_protection__change_your_pin);
      signalPinReminders.setEnabled(true);
      registrationLockV2.setEnabled(true);
    } else {
      signalPinCreateChange.setOnPreferenceClickListener(new KbsPinCreateListener());
      signalPinCreateChange.setTitle(R.string.preferences_app_protection__create_a_pin);
      signalPinReminders.setEnabled(false);
      registrationLockV2.setEnabled(false);
    }

    initializePhoneNumberPrivacyWhoCanSeeSummary();
    initializePhoneNumberPrivacyWhoCanFindSummary();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).setTextColor(Color.WHITE).show();
    }
  }

  private void initializePassphraseTimeoutSummary() {
    int timeoutMinutes = TextSecurePreferences.getPassphraseTimeoutInterval(getActivity());
    this.findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF)
        .setSummary(getResources().getQuantityString(R.plurals.AppProtectionPreferenceFragment_minutes, timeoutMinutes, timeoutMinutes));
  }

  private void initializeScreenLockTimeoutSummary() {
    long timeoutSeconds = TextSecurePreferences.getScreenLockTimeout(getContext());
    long hours          = TimeUnit.SECONDS.toHours(timeoutSeconds);
    long minutes        = TimeUnit.SECONDS.toMinutes(timeoutSeconds) - (TimeUnit.SECONDS.toHours(timeoutSeconds) * 60  );
    long seconds        = TimeUnit.SECONDS.toSeconds(timeoutSeconds) - (TimeUnit.SECONDS.toMinutes(timeoutSeconds) * 60);

    findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT)
        .setSummary(timeoutSeconds <= 0 ? getString(R.string.AppProtectionPreferenceFragment_none) :
                                          String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
  }

  private void initializePhoneNumberPrivacyWhoCanSeeSummary() {
    Preference preference = findPreference(PREFERENCE_WHO_CAN_SEE_PHONE_NUMBER);

    switch (SignalStore.phoneNumberPrivacy().getPhoneNumberSharingMode()) {
      case EVERYONE: preference.setSummary(R.string.PhoneNumberPrivacy_everyone);    break;
      case CONTACTS: preference.setSummary(R.string.PhoneNumberPrivacy_my_contacts); break;
      case NOBODY  : preference.setSummary(R.string.PhoneNumberPrivacy_nobody);      break;
      default      : throw new AssertionError();
    }
  }

  private void initializePhoneNumberPrivacyWhoCanFindSummary() {
    Preference preference = findPreference(PREFERENCE_WHO_CAN_FIND_BY_PHONE_NUMBER);

    switch (SignalStore.phoneNumberPrivacy().getPhoneNumberListingMode()) {
      case LISTED  : preference.setSummary(R.string.PhoneNumberPrivacy_everyone); break;
      case UNLISTED: preference.setSummary(R.string.PhoneNumberPrivacy_nobody);   break;
      default      : throw new AssertionError();
    }
  }

  private void initializeVisibility() {
    if (TextSecurePreferences.isPasswordDisabled(getContext())) {
      findPreference("pref_enable_passphrase_temporary").setVisible(false);
      findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF).setVisible(false);
      findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF).setVisible(false);
      findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_PREF).setVisible(false);

      KeyguardManager keyguardManager = (KeyguardManager)getContext().getSystemService(Context.KEYGUARD_SERVICE);
      if (!keyguardManager.isKeyguardSecure()) {
        ((SwitchPreferenceCompat)findPreference(TextSecurePreferences.SCREEN_LOCK)).setChecked(false);
        findPreference(TextSecurePreferences.SCREEN_LOCK).setEnabled(false);
      }
    } else {
      findPreference(TextSecurePreferences.SCREEN_LOCK).setVisible(false);
      findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT).setVisible(false);
    }
  }

  private class ScreenLockListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      Log.w(TAG, "Screen lock preference changed: " + newValue);

      boolean enabled = (Boolean)newValue;
      TextSecurePreferences.setScreenLockEnabled(getContext(), enabled);

      Intent intent = new Intent(getContext(), KeyCachingService.class);
      intent.setAction(KeyCachingService.LOCK_TOGGLED_EVENT);
      getContext().startService(intent);

      ConversationUtil.refreshRecipientShortcuts();

      return true;
    }
  }

  private class ScreenLockTimeoutListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      new TimeDurationPickerDialog(getContext(), (view, duration) -> {
        if (duration == 0) {
          TextSecurePreferences.setScreenLockTimeout(getContext(), 0);
        } else {
          long timeoutSeconds = Math.max(TimeUnit.MILLISECONDS.toSeconds(duration), 60);
          TextSecurePreferences.setScreenLockTimeout(getContext(), timeoutSeconds);
        }

        initializeScreenLockTimeoutSummary();
      }, 0).show();

      return true;
    }
  }

  private class KbsPinUpdateListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      startActivityForResult(CreateKbsPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN);
      return true;
    }
  }

  private class KbsPinCreateListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      startActivityForResult(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN);
      return true;
    }
  }

  private class BlockedContactsClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(getActivity(), BlockedUsersActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class ReadReceiptToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(enabled,
                                                                                          TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                                                                                          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                                                                                          SignalStore.settings().isLinkPreviewsEnabled()));

      });
      return true;
    }
  }

  private class TypingIndicatorsToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                                                                                          enabled,
                                                                                          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                                                                                          SignalStore.settings().isLinkPreviewsEnabled()));

        if (!enabled) {
          ApplicationDependencies.getTypingStatusRepository().clear();
        }
      });
      return true;
    }
  }

  private class LinkPreviewToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                                                                                          TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                                                                                          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(requireContext()),
                                                                                          enabled));
        if (enabled) {
          ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.LINK_PREVIEWS);
        }
      });
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    final   int    privacySummaryResId = R.string.ApplicationPreferencesActivity_privacy_summary;;
    final   String onRes               = context.getString(R.string.ApplicationPreferencesActivity_on);
    final   String offRes              = context.getString(R.string.ApplicationPreferencesActivity_off);
    boolean registrationLockEnabled    = RegistrationLockUtil.userHasRegistrationLock(context);

    if (TextSecurePreferences.isPasswordDisabled(context) && !TextSecurePreferences.isScreenLockEnabled(context)) {
      if (registrationLockEnabled) {
        return context.getString(privacySummaryResId, offRes, onRes);
      } else {
        return context.getString(privacySummaryResId, offRes, offRes);
      }
    } else {
      if (registrationLockEnabled) {
        return context.getString(privacySummaryResId, onRes, onRes);
      } else {
        return context.getString(privacySummaryResId, onRes, offRes);
      }
    }
  }

  // Derecated

  private class ChangePassphraseClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      if (MasterSecretUtil.isPassphraseInitialized(getActivity())) {
        startActivity(new Intent(getActivity(), PassphraseChangeActivity.class));
      } else {
        Toast.makeText(getActivity(),
                       R.string.ApplicationPreferenceActivity_you_havent_set_a_passphrase_yet,
                       Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

  private class PassphraseIntervalClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      new TimeDurationPickerDialog(getContext(), (view, duration) -> {
        int timeoutMinutes = Math.max((int)TimeUnit.MILLISECONDS.toMinutes(duration), 1);

        TextSecurePreferences.setPassphraseTimeoutInterval(getActivity(), timeoutMinutes);

        initializePassphraseTimeoutSummary();

      }, 0).show();

      return true;
    }
  }

  private class DisablePassphraseClickListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
      if (((CheckBoxPreference)preference).isChecked()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_passphrase);
        builder.setMessage(R.string.ApplicationPreferencesActivity_this_will_permanently_unlock_signal_and_message_notifications);
        builder.setIcon(R.drawable.ic_warning);
        builder.setPositiveButton(R.string.ApplicationPreferencesActivity_disable, (dialog, which) -> {
          MasterSecretUtil.changeMasterSecretPassphrase(getActivity(),
                                                        KeyCachingService.getMasterSecret(getContext()),
                                                        MasterSecretUtil.UNENCRYPTED_PASSPHRASE);

          TextSecurePreferences.setPasswordDisabled(getActivity(), true);
          ((CheckBoxPreference)preference).setChecked(false);

          Intent intent = new Intent(getActivity(), KeyCachingService.class);
          intent.setAction(KeyCachingService.DISABLE_ACTION);
          getActivity().startService(intent);

          initializeVisibility();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
      } else {
        Intent intent = new Intent(getActivity(), PassphraseChangeActivity.class);
        startActivity(intent);
      }

      return false;
    }
  }

  private class ShowUnidentifiedDeliveryIndicatorsChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (boolean) newValue;
      SignalExecutors.BOUNDED.execute(() -> {
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(getContext()),
                                                                                          TextSecurePreferences.isTypingIndicatorsEnabled(getContext()),
                                                                                          enabled,
                                                                                          SignalStore.settings().isLinkPreviewsEnabled()));
      });

      return true;
    }
  }

  private class UniversalUnidentifiedAccessChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return true;
    }
  }

  private class UnidentifiedLearnMoreClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      CommunicationActions.openBrowserLink(preference.getContext(), "https://signal.org/blog/sealed-sender/");
      return true;
    }
  }

  private class IncognitoLearnMoreClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      CommunicationActions.openBrowserLink(preference.getContext(), "https://support.signal.org/hc/en-us/articles/360055276112");
      return true;
    }
  }

  private class RegistrationLockV2ChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean     value   = (boolean) newValue;

      Log.i(TAG, "Getting ready to change registration lock setting to: " + value);

      if (value) {
        RegistrationLockV2Dialog.showEnableDialog(requireContext(), () -> ((CheckBoxPreference) preference).setChecked(true));
      } else {
        RegistrationLockV2Dialog.showDisableDialog(requireContext(), () -> ((CheckBoxPreference) preference).setChecked(false));
      }

      return false;
    }
  }

  private class PinRemindersChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean value = (boolean) newValue;

      if (!value) {
        Context        context = preference.getContext();
        DisplayMetrics metrics = preference.getContext().getResources().getDisplayMetrics();
        AlertDialog    dialog  = new AlertDialog.Builder(context, ThemeUtil.isDarkTheme(context) ? R.style.Theme_Signal_AlertDialog_Dark_Cornered_ColoredAccent : R.style.Theme_Signal_AlertDialog_Light_Cornered_ColoredAccent)
                                                .setView(R.layout.pin_disable_reminders_dialog)
                                                .create();


        dialog.show();
        dialog.getWindow().setLayout((int)(metrics.widthPixels * .80), ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText pinEditText   = (EditText) DialogCompat.requireViewById(dialog, R.id.reminder_disable_pin);
        TextView statusText    = (TextView) DialogCompat.requireViewById(dialog, R.id.reminder_disable_status);
        View     cancelButton  = DialogCompat.requireViewById(dialog, R.id.reminder_disable_cancel);
        View     turnOffButton = DialogCompat.requireViewById(dialog, R.id.reminder_disable_turn_off);

        pinEditText.post(() -> {
          if (pinEditText.requestFocus()) {
            ServiceUtil.getInputMethodManager(pinEditText.getContext()).showSoftInput(pinEditText, 0);
          }
        });

        ViewCompat.setAutofillHints(pinEditText, HintConstants.AUTOFILL_HINT_PASSWORD);

        switch (SignalStore.pinValues().getKeyboardType()) {
          case NUMERIC:
            pinEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            break;
          case ALPHA_NUMERIC:
            pinEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            break;
          default:
            throw new AssertionError("Unexpected type!");
        }

        pinEditText.addTextChangedListener(new SimpleTextWatcher() {
          @Override
          public void onTextChanged(String text) {
            turnOffButton.setEnabled(text.length() >= KbsConstants.MINIMUM_PIN_LENGTH);
          }
        });

        pinEditText.setTypeface(Typeface.DEFAULT);

        turnOffButton.setOnClickListener(v -> {
          String  pin     = pinEditText.getText().toString();
          boolean correct = PinHashing.verifyLocalPinHash(Objects.requireNonNull(SignalStore.kbsValues().getLocalPinHash()), pin);

          if (correct) {
            SignalStore.pinValues().setPinRemindersEnabled(false);
            ((SwitchPreferenceCompat) findPreference(PinValues.PIN_REMINDERS_ENABLED)).setChecked(false);
            dialog.dismiss();
          } else {
            statusText.setText(R.string.preferences_app_protection__incorrect_pin_try_again);
          }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        return false;
      } else {
        return true;
      }
    }
  }

  private final class PhoneNumberPrivacyWhoCanSeeClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      PhoneNumberPrivacyValues phoneNumberPrivacyValues = SignalStore.phoneNumberPrivacy();

      final PhoneNumberPrivacyValues.PhoneNumberSharingMode[] value = { phoneNumberPrivacyValues.getPhoneNumberSharingMode() };

      Map<PhoneNumberPrivacyValues.PhoneNumberSharingMode, CharSequence> items        = items(requireContext());
      List<PhoneNumberPrivacyValues.PhoneNumberSharingMode>              modes        = new ArrayList<>(items.keySet());
      CharSequence[]                                                     modeStrings  = items.values().toArray(new CharSequence[0]);
      int                                                                selectedMode = modes.indexOf(value[0]);

      new AlertDialog.Builder(requireActivity())
                     .setTitle(R.string.preferences_app_protection__see_my_phone_number)
                     .setCancelable(true)
                     .setSingleChoiceItems(modeStrings, selectedMode, (dialog, which) -> value[0] = modes.get(which))
                     .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                       PhoneNumberPrivacyValues.PhoneNumberSharingMode phoneNumberSharingMode = value[0];
                       phoneNumberPrivacyValues.setPhoneNumberSharingMode(phoneNumberSharingMode);
                       Log.i(TAG, String.format("PhoneNumberSharingMode changed to %s. Scheduling storage value sync", phoneNumberSharingMode));
                       StorageSyncHelper.scheduleSyncForDataChange();
                       initializePhoneNumberPrivacyWhoCanSeeSummary();
                     })
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();

      return true;
    }

    private Map<PhoneNumberPrivacyValues.PhoneNumberSharingMode, CharSequence> items(Context context) {
      Map<PhoneNumberPrivacyValues.PhoneNumberSharingMode, CharSequence> map = new LinkedHashMap<>();

      map.put(PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYONE, titleAndDescription(context, context.getString(R.string.PhoneNumberPrivacy_everyone), context.getString(R.string.PhoneNumberPrivacy_everyone_see_description)));
      map.put(PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY, context.getString(R.string.PhoneNumberPrivacy_nobody));

      return map;
    }
  }

  private final class PhoneNumberPrivacyWhoCanFindClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      PhoneNumberPrivacyValues phoneNumberPrivacyValues = SignalStore.phoneNumberPrivacy();

      final PhoneNumberPrivacyValues.PhoneNumberListingMode[] value = { phoneNumberPrivacyValues.getPhoneNumberListingMode() };

      new AlertDialog.Builder(requireActivity())
                     .setTitle(R.string.preferences_app_protection__find_me_by_phone_number)
                     .setCancelable(true)
                     .setSingleChoiceItems(items(requireContext()),
                                           value[0].ordinal(),
                                           (dialog, which) -> value[0] = PhoneNumberPrivacyValues.PhoneNumberListingMode.values()[which])
                     .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                       PhoneNumberPrivacyValues.PhoneNumberListingMode phoneNumberListingMode = value[0];
                       phoneNumberPrivacyValues.setPhoneNumberListingMode(phoneNumberListingMode);
                       Log.i(TAG, String.format("PhoneNumberListingMode changed to %s. Scheduling storage value sync", phoneNumberListingMode));
                       StorageSyncHelper.scheduleSyncForDataChange();
                       ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
                       initializePhoneNumberPrivacyWhoCanFindSummary();
                     })
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();

      return true;
    }

    private CharSequence[] items(Context context) {
      return new CharSequence[]{
        titleAndDescription(context, context.getString(R.string.PhoneNumberPrivacy_everyone), context.getString(R.string.PhoneNumberPrivacy_everyone_find_description)),
        context.getString(R.string.PhoneNumberPrivacy_nobody) };
    }
  }

  /** Adds a detail row for radio group descriptions. */
  private static CharSequence titleAndDescription(@NonNull Context context, @NonNull String header, @NonNull String description) {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    builder.append("\n");
    builder.append(header);
    builder.append("\n");

    builder.setSpan(new TextAppearanceSpan(context, android.R.style.TextAppearance_Small), builder.length(), builder.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

    builder.append(description);
    builder.append("\n");

    return builder;
  }
}
