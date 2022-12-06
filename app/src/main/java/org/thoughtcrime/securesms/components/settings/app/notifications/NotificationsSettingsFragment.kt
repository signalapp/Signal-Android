package org.thoughtcrime.securesms.components.settings.app.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.PreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.RadioListPreference
import org.thoughtcrime.securesms.components.settings.RadioListPreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.util.RingtoneUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private const val MESSAGE_SOUND_SELECT: Int = 1
private const val CALL_RINGTONE_SELECT: Int = 2
private val TAG = Log.tag(NotificationsSettingsFragment::class.java)

class NotificationsSettingsFragment : DSLSettingsFragment(R.string.preferences__notifications) {

  private val repeatAlertsValues by lazy { resources.getStringArray(R.array.pref_repeat_alerts_values) }
  private val repeatAlertsLabels by lazy { resources.getStringArray(R.array.pref_repeat_alerts_entries) }

  private val notificationPrivacyValues by lazy { resources.getStringArray(R.array.pref_notification_privacy_values) }
  private val notificationPrivacyLabels by lazy { resources.getStringArray(R.array.pref_notification_privacy_entries) }

  private val notificationPriorityValues by lazy { resources.getStringArray(R.array.pref_notification_priority_values) }
  private val notificationPriorityLabels by lazy { resources.getStringArray(R.array.pref_notification_priority_entries) }

  private val ledColorValues by lazy { resources.getStringArray(R.array.pref_led_color_values) }
  private val ledColorLabels by lazy { resources.getStringArray(R.array.pref_led_color_entries) }

  private val ledBlinkValues by lazy { resources.getStringArray(R.array.pref_led_blink_pattern_values) }
  private val ledBlinkLabels by lazy { resources.getStringArray(R.array.pref_led_blink_pattern_entries) }

  private lateinit var viewModel: NotificationsSettingsViewModel

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == MESSAGE_SOUND_SELECT && resultCode == Activity.RESULT_OK && data != null) {
      val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
      viewModel.setMessageNotificationsSound(uri)
    } else if (requestCode == CALL_RINGTONE_SELECT && resultCode == Activity.RESULT_OK && data != null) {
      val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
      viewModel.setCallRingtone(uri)
    }
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    adapter.registerFactory(
      LedColorPreference::class.java,
      LayoutFactory(::LedColorPreferenceViewHolder, R.layout.dsl_preference_item)
    )

    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    val factory = NotificationsSettingsViewModel.Factory(sharedPreferences)

    viewModel = ViewModelProvider(this, factory)[NotificationsSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  private fun getConfiguration(state: NotificationsSettingsState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.NotificationsSettingsFragment__messages)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__notifications),
        isChecked = state.messageNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setMessageNotificationsEnabled(!state.messageNotificationsState.notificationsEnabled)
        }
      )

      if (Build.VERSION.SDK_INT >= 30) {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__customize),
          summary = DSLSettingsText.from(R.string.preferences__change_sound_and_vibration),
          isEnabled = state.messageNotificationsState.notificationsEnabled,
          onClick = {
            NotificationChannels.getInstance().openChannelSettings(requireActivity(), NotificationChannels.getInstance().messagesChannel, null)
          }
        )
      } else {

        clickPref(
          title = DSLSettingsText.from(R.string.preferences__sound),
          summary = DSLSettingsText.from(getRingtoneSummary(state.messageNotificationsState.sound)),
          isEnabled = state.messageNotificationsState.notificationsEnabled,
          onClick = {
            launchMessageSoundSelectionIntent()
          }
        )

        switchPref(
          title = DSLSettingsText.from(R.string.preferences__vibrate),
          isChecked = state.messageNotificationsState.vibrateEnabled,
          isEnabled = state.messageNotificationsState.notificationsEnabled,
          onClick = {
            viewModel.setMessageNotificationVibration(!state.messageNotificationsState.vibrateEnabled)
          }
        )

        customPref(
          LedColorPreference(
            colorValues = ledColorValues,
            radioListPreference = RadioListPreference(
              title = DSLSettingsText.from(R.string.preferences__led_color),
              listItems = ledColorLabels,
              selected = ledColorValues.indexOf(state.messageNotificationsState.ledColor),
              isEnabled = state.messageNotificationsState.notificationsEnabled,
              onSelected = {
                viewModel.setMessageNotificationLedColor(ledColorValues[it])
              }
            )
          )
        )

        if (!NotificationChannels.supported()) {
          radioListPref(
            title = DSLSettingsText.from(R.string.preferences__pref_led_blink_title),
            listItems = ledBlinkLabels,
            selected = ledBlinkValues.indexOf(state.messageNotificationsState.ledBlink),
            isEnabled = state.messageNotificationsState.notificationsEnabled,
            onSelected = {
              viewModel.setMessageNotificationLedBlink(ledBlinkValues[it])
            }
          )
        }
      }

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_notifications__in_chat_sounds),
        isChecked = state.messageNotificationsState.inChatSoundsEnabled,
        isEnabled = state.messageNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setMessageNotificationInChatSoundsEnabled(!state.messageNotificationsState.inChatSoundsEnabled)
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__repeat_alerts),
        listItems = repeatAlertsLabels,
        selected = repeatAlertsValues.indexOf(state.messageNotificationsState.repeatAlerts.toString()),
        isEnabled = state.messageNotificationsState.notificationsEnabled,
        onSelected = {
          viewModel.setMessageRepeatAlerts(repeatAlertsValues[it].toInt())
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences_notifications__show),
        listItems = notificationPrivacyLabels,
        selected = notificationPrivacyValues.indexOf(state.messageNotificationsState.messagePrivacy),
        isEnabled = state.messageNotificationsState.notificationsEnabled,
        onSelected = {
          viewModel.setMessageNotificationPrivacy(notificationPrivacyValues[it])
        }
      )

      if (Build.VERSION.SDK_INT < 30) {
        if (NotificationChannels.supported()) {
          clickPref(
            title = DSLSettingsText.from(R.string.preferences_notifications__priority),
            isEnabled = state.messageNotificationsState.notificationsEnabled,
            onClick = {
              launchNotificationPriorityIntent()
            }
          )
        } else {
          radioListPref(
            title = DSLSettingsText.from(R.string.preferences_notifications__priority),
            listItems = notificationPriorityLabels,
            selected = notificationPriorityValues.indexOf(state.messageNotificationsState.priority.toString()),
            isEnabled = state.messageNotificationsState.notificationsEnabled,
            onSelected = {
              viewModel.setMessageNotificationPriority(notificationPriorityValues[it].toInt())
            }
          )
        }
      }

      dividerPref()

      sectionHeaderPref(R.string.NotificationsSettingsFragment__calls)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__notifications),
        isChecked = state.callNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setCallNotificationsEnabled(!state.callNotificationsState.notificationsEnabled)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_notifications__ringtone),
        summary = DSLSettingsText.from(getRingtoneSummary(state.callNotificationsState.ringtone)),
        isEnabled = state.callNotificationsState.notificationsEnabled,
        onClick = {
          launchCallRingtoneSelectionIntent()
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__vibrate),
        isChecked = state.callNotificationsState.vibrateEnabled,
        isEnabled = state.callNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setCallVibrateEnabled(!state.callNotificationsState.vibrateEnabled)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.NotificationsSettingsFragment__notification_profiles)

      clickPref(
        title = DSLSettingsText.from(R.string.NotificationsSettingsFragment__profiles),
        summary = DSLSettingsText.from(R.string.NotificationsSettingsFragment__create_a_profile_to_receive_notifications_only_from_people_and_groups_you_choose),
        onClick = {
          findNavController().safeNavigate(R.id.action_notificationsSettingsFragment_to_notificationProfilesFragment)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.NotificationsSettingsFragment__notify_when)

      switchPref(
        title = DSLSettingsText.from(R.string.NotificationsSettingsFragment__contact_joins_signal),
        isChecked = state.notifyWhenContactJoinsSignal,
        onClick = {
          viewModel.setNotifyWhenContactJoinsSignal(!state.notifyWhenContactJoinsSignal)
        }
      )
    }
  }

  private fun getRingtoneSummary(uri: Uri): String {
    return if (TextUtils.isEmpty(uri.toString())) {
      getString(R.string.preferences__silent)
    } else {
      val tone: Ringtone? = RingtoneUtil.getRingtone(requireContext(), uri)
      if (tone != null) {
        try {
          tone.getTitle(requireContext()) ?: getString(R.string.NotificationsSettingsFragment__unknown_ringtone)
        } catch (e: SecurityException) {
          Log.w(TAG, "Unable to get title for ringtone", e)
          return getString(R.string.NotificationsSettingsFragment__unknown_ringtone)
        }
      } else {
        getString(R.string.preferences__default)
      }
    }
  }

  private fun launchMessageSoundSelectionIntent() {
    val current = SignalStore.settings().messageNotificationSound

    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
    intent.putExtra(
      RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
      Settings.System.DEFAULT_NOTIFICATION_URI
    )
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)

    openRingtonePicker(intent, MESSAGE_SOUND_SELECT)
  }

  @RequiresApi(26)
  private fun launchNotificationPriorityIntent() {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
    intent.putExtra(
      Settings.EXTRA_CHANNEL_ID,
      NotificationChannels.getInstance().messagesChannel
    )
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
    startActivity(intent)
  }

  private fun launchCallRingtoneSelectionIntent() {
    val current = SignalStore.settings().callRingtone

    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
    intent.putExtra(
      RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
      Settings.System.DEFAULT_RINGTONE_URI
    )
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)

    openRingtonePicker(intent, CALL_RINGTONE_SELECT)
  }

  @Suppress("DEPRECATION")
  private fun openRingtonePicker(intent: Intent, requestCode: Int) {
    try {
      startActivityForResult(intent, requestCode)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), R.string.NotificationSettingsFragment__failed_to_open_picker, Toast.LENGTH_LONG).show()
    }
  }

  private class LedColorPreference(
    val colorValues: Array<String>,
    val radioListPreference: RadioListPreference
  ) : PreferenceModel<LedColorPreference>(
    title = radioListPreference.title,
    icon = radioListPreference.icon,
    summary = radioListPreference.summary
  ) {
    override fun areContentsTheSame(newItem: LedColorPreference): Boolean {
      return super.areContentsTheSame(newItem) && radioListPreference.areContentsTheSame(newItem.radioListPreference)
    }
  }

  private class LedColorPreferenceViewHolder(itemView: View) :
    PreferenceViewHolder<LedColorPreference>(itemView) {

    val radioListPreferenceViewHolder = RadioListPreferenceViewHolder(itemView)

    override fun bind(model: LedColorPreference) {
      super.bind(model)
      radioListPreferenceViewHolder.bind(model.radioListPreference)

      summaryView.visibility = View.GONE

      val circleDrawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.circle_tintable))
      circleDrawable.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20))
      circleDrawable.colorFilter = model.colorValues[model.radioListPreference.selected].toColorFilter()

      if (ViewUtil.isLtr(itemView)) {
        titleView.setCompoundDrawables(null, null, circleDrawable, null)
      } else {
        titleView.setCompoundDrawables(circleDrawable, null, null, null)
      }
    }

    private fun String.toColorFilter(): ColorFilter {
      val color = when (this) {
        "green" -> ContextCompat.getColor(context, R.color.green_500)
        "red" -> ContextCompat.getColor(context, R.color.red_500)
        "blue" -> ContextCompat.getColor(context, R.color.blue_500)
        "yellow" -> ContextCompat.getColor(context, R.color.yellow_500)
        "cyan" -> ContextCompat.getColor(context, R.color.cyan_500)
        "magenta" -> ContextCompat.getColor(context, R.color.pink_500)
        "white" -> ContextCompat.getColor(context, R.color.white)
        else -> ContextCompat.getColor(context, R.color.transparent)
      }

      return PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
  }
}
