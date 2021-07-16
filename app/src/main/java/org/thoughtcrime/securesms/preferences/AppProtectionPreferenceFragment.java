package org.thoughtcrime.securesms.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.DialogCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.blocked.BlockedUsersActivity;
import org.thoughtcrime.securesms.components.Mp02CustomDialog;
import org.thoughtcrime.securesms.components.Mp03CustomDialog;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.PinValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.PinHashing;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.KbsConstants;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.pin.PinState;
import org.thoughtcrime.securesms.pin.RegistrationLockV2Dialog;
import org.thoughtcrime.securesms.preferences.widgets.Mp02CommonPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.Objects;

public class AppProtectionPreferenceFragment extends CorrectedPreferenceFragment implements Preference.OnPreferenceClickListener {

  private static final String TAG = Log.tag(AppProtectionPreferenceFragment.class);

  private static final String PREFERENCE_CATEGORY_BLOCKED             = "preference_category_blocked";
  private static final String PREFERENCE_UNIDENTIFIED_LEARN_MORE      = "pref_unidentified_learn_more";
  private static final String PREFERENCE_WHO_CAN_SEE_PHONE_NUMBER     = "pref_who_can_see_phone_number";
  private static final String PREFERENCE_WHO_CAN_FIND_BY_PHONE_NUMBER = "pref_who_can_find_by_phone_number";

  private Mp02CommonPreference mRelayCallsPref;
  private Mp02CommonPreference mReadReceiptsPref;
  private Mp02CommonPreference mTypingIndicatorsPref;
  private Mp02CommonPreference mSendLinkPreviewsPref;
  private Mp02CommonPreference mBlockedContactsPref;
  private Mp02CommonPreference mDisplayIndicatorsPref;
  private Mp02CommonPreference mAllowFromAnyonePref;
  private Mp02CommonPreference mLearnMorePref;

  //pin
  private Mp02CommonPreference mPINNotificationPref;
  private Mp02CommonPreference mPINLockPref;
  private Mp02CommonPreference mPINSetPref;

  private boolean mRelayCallsEnabled;
  private boolean mReadReceiptsEnabled;
  private boolean mTypingIndicatorsEnabled;
  private boolean mSendLinkPreviewsEnabled;
  private boolean mDisplayIndicatorsEnabled;
  private boolean mAllowFromAnyoneEnabled;
  private boolean mPinNotificationEnabled;
  private boolean mPinChangeEnabled;
  private boolean mPinLockedEnabled;

  private PreferenceGroup mPrefGroup;
  private int mPrefCount;
  private int mCurPoi = 1;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    mRelayCallsPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.ALWAYS_RELAY_CALLS_PREF);
    mRelayCallsPref.setOnPreferenceClickListener(this);

    mReadReceiptsPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.READ_RECEIPTS_PREF);
    mReadReceiptsPref.setOnPreferenceClickListener(this);

    mTypingIndicatorsPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.TYPING_INDICATORS);
    mTypingIndicatorsPref.setOnPreferenceClickListener(this);

    mSendLinkPreviewsPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.LINK_PREVIEWS);
    mSendLinkPreviewsPref.setOnPreferenceClickListener(this);
    mSendLinkPreviewsPref.setPreferenceDataStore(SignalStore.getPreferenceDataStore());

    mBlockedContactsPref = (Mp02CommonPreference) findPreference(PREFERENCE_CATEGORY_BLOCKED);
    mBlockedContactsPref.setOnPreferenceClickListener(this);

    mDisplayIndicatorsPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS);
    mDisplayIndicatorsPref.setOnPreferenceClickListener(this);

    mAllowFromAnyonePref = (Mp02CommonPreference) findPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS);
    mAllowFromAnyonePref.setOnPreferenceClickListener(this);
    mAllowFromAnyonePref.setOnPreferenceChangeListener(new UniversalUnidentifiedAccessChangedListener());

    //pin 1
    mPINSetPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.SIGNAL_PIN_CHANGE);
    mPINSetPref.setOnPreferenceClickListener(this);
    //pin 2
    mPINNotificationPref = (Mp02CommonPreference) findPreference(PinValues.PIN_REMINDERS_ENABLED);
    mPINNotificationPref.setOnPreferenceClickListener(this);

    //pin 3
    mPINLockPref = (Mp02CommonPreference) findPreference(KbsValues.V2_LOCK_ENABLED);
    mPINLockPref.setOnPreferenceClickListener(this);

    mLearnMorePref = (Mp02CommonPreference) findPreference(PREFERENCE_UNIDENTIFIED_LEARN_MORE);
    mLearnMorePref.setOnPreferenceClickListener(this);

    mPrefGroup = getPreferenceGroup();
    mPrefCount = mPrefGroup.getPreferenceCount();

    if (SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut()) {
      mPINSetPref.setOnPreferenceClickListener(new KbsPinUpdateListener());
      mPINSetPref.setTitle(R.string.preferences_app_protection__change_your_pin);
      mPINNotificationPref.setEnabled(true);
      mPINLockPref.setEnabled(true);
    } else {
      mPINSetPref.setOnPreferenceClickListener(new KbsPinCreateListener());
      mPINSetPref.setTitle(R.string.preferences_app_protection__create_a_pin);
      mPINNotificationPref.setEnabled(false);
      mPINLockPref.setEnabled(false);
    }

    //Update Pref Status
    updatePrefStatus(TextSecurePreferences.ALWAYS_RELAY_CALLS_PREF, TextSecurePreferences.isTurnOnly(getContext()));
    updatePrefStatus(TextSecurePreferences.READ_RECEIPTS_PREF, TextSecurePreferences.isReadReceiptsEnabled(getContext()));
    updatePrefStatus(TextSecurePreferences.TYPING_INDICATORS, TextSecurePreferences.isTypingIndicatorsEnabled(getContext()));
    updatePrefStatus(TextSecurePreferences.LINK_PREVIEWS, SignalStore.settings().isLinkPreviewsEnabled());
    updatePrefStatus(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()));
    updatePrefStatus(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS, TextSecurePreferences.isUniversalUnidentifiedAccess(getContext()));

    //pin Notification
    updatePrefStatus(PinValues.PIN_REMINDERS_ENABLED,SignalStore.pinValues().arePinRemindersEnabled());
    //pin lock
    updatePrefStatus(KbsValues.V2_LOCK_ENABLED,SignalStore.kbsValues().isV2RegistrationLockEnabled());
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = super.onCreateView(inflater, container, savedInstanceState);
    return view;
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_app_protection);
  }

  private void updatePrefStatus(String key, boolean enabled) {
    String status = enabled ?  " " + PREF_STATUS_ON : " " + PREF_STATUS_OFF;
    switch (key) {
      case TextSecurePreferences.ALWAYS_RELAY_CALLS_PREF:
        mRelayCallsEnabled = enabled;
        mRelayCallsPref.setTitle(getString(R.string.preferences_advanced__always_relay_calls) + status);
        break;
      case TextSecurePreferences.READ_RECEIPTS_PREF:
        mReadReceiptsEnabled = enabled;
        mReadReceiptsPref.setTitle(getString(R.string.preferences__read_receipts) + status);
        break;
      case TextSecurePreferences.TYPING_INDICATORS:
        mTypingIndicatorsEnabled = enabled;
        mTypingIndicatorsPref.setTitle(getString(R.string.preferences__typing_indicators) + status);
        break;
      case TextSecurePreferences.LINK_PREVIEWS:
        mSendLinkPreviewsEnabled = enabled;
        mSendLinkPreviewsPref.setTitle(getString(R.string.preferences__generate_link_previews) + status);
        break;
      case TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS:
        mDisplayIndicatorsEnabled = enabled;
        mDisplayIndicatorsPref.setTitle(getString(R.string.preferences_communication__sealed_sender_display_indicators) + status);
        break;
      case TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS:
        mAllowFromAnyoneEnabled = enabled;
        mAllowFromAnyonePref.setTitle(getString(R.string.preferences_communication__sealed_sender_allow_from_anyone) + status);
        break;
      case PinValues.PIN_REMINDERS_ENABLED:
        mPinNotificationEnabled = enabled;
        mPINNotificationPref.setTitle(getString(R.string.preferences_app_protection__pin_reminders) + status);
        break;
      case KbsValues.V2_LOCK_ENABLED:
        mPinLockedEnabled = enabled;
        mPINLockPref.setTitle(getString(R.string.preferences_app_protection__registration_lock) + status);
        break;
    }
  }

  @Override
  public boolean onKeyDown(KeyEvent event) {
    View v = getListView().getFocusedChild();
    if (v != null) {
      mCurPoi = (int) v.getTag();
    }
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_DOWN:
        if (mCurPoi < mPrefCount - 1) {
          mCurPoi++;
          changeView(mCurPoi, true);
        }
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
        if (mCurPoi > 1) {
          mCurPoi--;
          changeView(mCurPoi, false);
        }
        break;
    }
    return super.onKeyDown(event);
  }

  private void changeView(int currentPosition, boolean b) {
    Preference preference = mPrefGroup.getPreference(currentPosition);
    Preference preference1 = null;
    Preference preference2 = null;
    if (currentPosition > 0) {
      preference1 = mPrefGroup.getPreference(currentPosition - 1);
    }
    if (currentPosition < mPrefCount - 1) {
      preference2 = mPrefGroup.getPreference(currentPosition + 1);
    }

    String curTitle = "";
    String title1 = "";
    String title2 = "";
    curTitle = preference.getTitle().toString();
    if (preference1 != null) {
      title1 = preference1.getTitle().toString();
    }
    if (preference2 != null) {
      title2 = preference2.getTitle().toString();
    }
  }

  @Override
  public boolean onPreferenceClick(Preference preference) {
    String key = preference.getKey();
    switch (key) {
      case TextSecurePreferences.ALWAYS_RELAY_CALLS_PREF:
        TextSecurePreferences.setTurnOnly(getContext(), !mRelayCallsEnabled);
        updatePrefStatus(TextSecurePreferences.ALWAYS_RELAY_CALLS_PREF, TextSecurePreferences.isTurnOnly(getContext()));
        break;
      case TextSecurePreferences.READ_RECEIPTS_PREF:
        TextSecurePreferences.setReadReceiptsEnabled(getContext(), !mReadReceiptsEnabled);
        updatePrefStatus(TextSecurePreferences.READ_RECEIPTS_PREF, TextSecurePreferences.isReadReceiptsEnabled(getContext()));
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(mReadReceiptsEnabled,
                TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                SignalStore.settings().isLinkPreviewsEnabled()));
        break;
      case TextSecurePreferences.TYPING_INDICATORS:
        TextSecurePreferences.setTypingIndicatorsEnabled(getContext(), !mTypingIndicatorsEnabled);
        updatePrefStatus(TextSecurePreferences.TYPING_INDICATORS, TextSecurePreferences.isTypingIndicatorsEnabled(getContext()));
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                mTypingIndicatorsEnabled,
                TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                SignalStore.settings().isLinkPreviewsEnabled()));

        if (!TextSecurePreferences.isTypingIndicatorsEnabled(getContext())) {
          ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().clear();
        }
        break;
      case TextSecurePreferences.LINK_PREVIEWS:
        updatePrefStatus(TextSecurePreferences.LINK_PREVIEWS, !mSendLinkPreviewsEnabled);
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(requireContext()),
                mSendLinkPreviewsEnabled));
        SignalStore.settings().setLinkPreviewsEnabled(mSendLinkPreviewsEnabled);
        if (mSendLinkPreviewsEnabled) {
          ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.LINK_PREVIEWS);
        }
        break;
      case PREFERENCE_CATEGORY_BLOCKED:
        Intent intent = new Intent(getActivity(), BlockedUsersActivity.class);
        startActivity(intent);
        break;
      case TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS:
        TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(getContext(), !mDisplayIndicatorsEnabled);
        updatePrefStatus(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()));
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(getContext()),
                TextSecurePreferences.isTypingIndicatorsEnabled(getContext()),
                mDisplayIndicatorsEnabled,
                SignalStore.settings().isLinkPreviewsEnabled()));
        break;
      case TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS:
        TextSecurePreferences.setUniversalUnidentifiedAccess(getContext(), !mAllowFromAnyoneEnabled);
        updatePrefStatus(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS, TextSecurePreferences.isUniversalUnidentifiedAccess(getContext()));
        break;
      case PREFERENCE_UNIDENTIFIED_LEARN_MORE:
        Toast.makeText(getContext(), "https://signal.org/blog/sealed-sender/", Toast.LENGTH_LONG).show();
        break;
      case PinValues.PIN_REMINDERS_ENABLED:
        changePinValue(preference,!mPinNotificationEnabled);
        break;
      case KbsValues.V2_LOCK_ENABLED:
        changePinLock(!SignalStore.kbsValues().isV2RegistrationLockEnabled());
        break;
    }
    return true;
  }

  private class UniversalUnidentifiedAccessChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return true;
    }
  }


  @SuppressLint("ResourceType")
  public boolean changePinValue(Preference preference, Object newValue) {

    boolean value = (boolean) newValue;

    if (!value) {
      Context context = preference.getContext();
      Mp03CustomDialog mp03CustomDialog = new Mp03CustomDialog(getContext());

      mp03CustomDialog.setTitle(getActivity().getResources().getString(R.string.preferences_app_protection__confirm_your_signal_pin));

      mp03CustomDialog.setMessage(getActivity().getResources().getString(R.string.preferences_app_protection__make_sure_you_memorize_or_securely_store_your_pin));

      mp03CustomDialog.setPositiveListener(R.string.preferences_app_protection__turn_off, () -> {
        EditText pinEditText = mp03CustomDialog.findViewById(R.id.reminder_disable_pin_0);
        String pin = pinEditText.getText().toString();
        if(pin.length() < KbsConstants.MINIMUM_PIN_LENGTH) {
          Toast.makeText(context, getActivity().getResources().getString(R.string.preferences_app_protection__incorrect_pin_try_again),Toast.LENGTH_SHORT).show();
          return 0;
        }
        boolean correct = PinHashing.verifyLocalPinHash(Objects.requireNonNull(SignalStore.kbsValues().getLocalPinHash()), pin);
        if (correct) {
          SignalStore.pinValues().setPinRemindersEnabled(false);
          updatePrefStatus(PinValues.PIN_REMINDERS_ENABLED, SignalStore.pinValues().arePinRemindersEnabled());
          return 1;
        } else  {
          Toast.makeText(context, getActivity().getResources().getString(R.string.preferences_app_protection__incorrect_pin_try_again),Toast.LENGTH_SHORT).show();
          return 0;
        }
      });
      mp03CustomDialog.setNegativeListener(android.R.string.cancel, () -> {
        return 1;
      });
      mp03CustomDialog.setBackKeyListener(() -> {
        getActivity().finish();
      });
      mp03CustomDialog.show();

      EditText pinEditText = mp03CustomDialog.findViewById(R.id.reminder_disable_pin_0);
      if (pinEditText.requestFocus()) {
        ServiceUtil.getInputMethodManager(pinEditText.getContext()).showSoftInput(pinEditText, 0);
      }
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
          //mp03CustomDialog.findViewById(R.id.reminder_disable_turn_off).setEnabled(text.length() >= KbsConstants.MINIMUM_PIN_LENGTH);
        }
      });

      return false;
    } else {
      SignalStore.pinValues().setPinRemindersEnabled(true);
      updatePrefStatus(PinValues.PIN_REMINDERS_ENABLED, SignalStore.pinValues().arePinRemindersEnabled());
      return true;
    }
  }

  public void changePinLock(boolean value){
    if (value) {
      showEnableDialog(requireContext());
    } else {
      showDisableDialog(requireContext());
    }
  }

  private void showEnableDialog(Context context) {
    int titleRes = R.string.RegistrationLockV2Dialog_turn_on_registration_lock;
    int bodyRes = R.string.RegistrationLockV2Dialog_if_you_forget_your_signal_pin_when_registering_again;

    Mp02CustomDialog dialog = new Mp02CustomDialog(context);
    dialog.setMessage(getString(titleRes) + '\n' + getString(bodyRes));
    dialog.setCancelable(true);
    dialog.setPositiveListener(R.string.RegistrationLockV2Dialog_turn_on, () -> {

      SimpleTask.run(SignalExecutors.UNBOUNDED, () -> {
        try {
          PinState.onEnableRegistrationLockForUserWithPin();
          Log.i(TAG, "Successfully enabled registration lock.");
          return true;
        } catch (IOException e) {
          Log.w(TAG, "Failed to enable registration lock setting.", e);
          return false;
        }
      }, (success) -> {

        if (!success) {
          Toast.makeText(context, R.string.preferences_app_protection__failed_to_enable_registration_lock, Toast.LENGTH_LONG).show();
        } else {
          updatePrefStatus(KbsValues.V2_LOCK_ENABLED,true);
        }

        dialog.dismiss();
      });
    });

    dialog.setNegativeListener(android.R.string.cancel, null);
    dialog.show();
  }

  private void showDisableDialog(Context context) {
    int titleRes = R.string.RegistrationLockV2Dialog_turn_off_registration_lock;

    Mp02CustomDialog dialog = new Mp02CustomDialog(context);
    dialog.setMessage(getString(titleRes));
    dialog.setCancelable(true);
    dialog.setPositiveListener(R.string.RegistrationLockV2Dialog_turn_off, () -> {

      SimpleTask.run(SignalExecutors.UNBOUNDED, () -> {
        try {
          PinState.onDisableRegistrationLockForUserWithPin();
          Log.i(TAG, "Successfully disabled registration lock.");
          return true;
        } catch (IOException e) {
          Log.w(TAG, "Failed to disable registration lock.", e);
          return false;
        }
      }, (success) -> {

        if (!success) {
          Toast.makeText(context, R.string.preferences_app_protection__failed_to_disable_registration_lock, Toast.LENGTH_LONG).show();
        } else {
          updatePrefStatus(KbsValues.V2_LOCK_ENABLED,false);
        }

        dialog.dismiss();
      });
    });

    dialog.setNegativeListener(android.R.string.cancel, null);
    dialog.show();
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

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
//            Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).setTextColor(Color.WHITE).show();
      Toast.makeText(getContext(), R.string.ConfirmKbsPinFragment__pin_created, Toast.LENGTH_SHORT).show();
    }
  }

}
